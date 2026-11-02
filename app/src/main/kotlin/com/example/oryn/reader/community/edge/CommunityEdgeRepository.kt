package com.example.oryn.reader.community.edge

import com.example.oryn.reader.community.CommunityConfiguration
import com.example.oryn.reader.community.CommunitySessionProvider
import com.example.oryn.reader.community.PreparedCommunityDownload
import com.example.oryn.reader.community.auth.AuthConfiguration
import com.example.oryn.reader.community.auth.AuthStorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** No preference or injected production flag can grant permission to execute community requests. */
internal object CommunityEdgeSafety {
    private const val SECURITY_VALIDATED = false
    fun enabled(config: CommunityConfiguration): Boolean = SECURITY_VALIDATED && config.isAvailable && AuthConfiguration.fromBuild().isEnabled
}

/** Defense in depth against using the API directly rather than through its repository. */
internal class GuardedCommunityEdgeApi(private val config: CommunityConfiguration) : CommunityEdgeApi {
    private val protocol by lazy { CommunityEdgeProtocol(HttpsCommunityEdgeHttp(CommunityEdgeOrigin(config.url, config.publishableKey))) }
    private fun requireEnabled() { if (!CommunityEdgeSafety.enabled(config)) throw CommunityEdgeDisabled() }
    override suspend fun create(input: CreateCommunityUploadInput, token: CommunityUserToken): CommunityUploadTicket {
        requireEnabled(); return protocol.create(input, token)
    }
    override suspend fun finalize(bookId: UUID, token: CommunityUserToken): CommunityFinalization {
        requireEnabled(); return protocol.finalize(bookId, token)
    }
    override suspend fun retract(bookId: UUID, token: CommunityUserToken): CommunityRetraction {
        requireEnabled(); return protocol.retract(bookId, token)
    }
    override suspend fun download(plan: PreparedCommunityDownload, token: CommunityUserToken): IntegrityCheckedCommunityEpub {
        requireEnabled(); return protocol.download(plan, token)
    }
    override suspend fun moderate(bookId: UUID, decision: CommunityModerationDecision, token: CommunityUserToken): CommunityModeration {
        requireEnabled(); return protocol.moderate(bookId, decision, token)
    }
}

/** Server-verified capability must be bound to the current session. Never derive it from user_metadata.
 * No production implementation can currently grant it. Server authorization remains mandatory.
 */
internal fun interface CommunityModeratorAuthorization {
    suspend fun verifiedFor(token: CommunityUserToken): Boolean
}
internal object DeniedCommunityModeration : CommunityModeratorAuthorization {
    override suspend fun verifiedFor(token: CommunityUserToken) = false
}

/** Injectable orchestration core, used by production only behind both hard gates. No retry/replay. */
internal class CommunityEdgeOperationsCore(
    private val api: CommunityEdgeApi,
    private val session: CommunitySessionProvider,
    private val moderators: CommunityModeratorAuthorization = DeniedCommunityModeration
) : CommunityUserOperations, CommunityModeratorOperations {
    override suspend fun createUpload(input: CreateCommunityUploadInput) = perform(mutating = true) { token ->
        if (!input.share.consumeForCreation()) throw CommunityConsentRequired()
        api.create(input, token)
    }
    override suspend fun finalizeUpload(bookId: UUID) = perform(mutating = true) { token -> api.finalize(bookId, token) }
    override suspend fun retract(bookId: UUID) = perform(mutating = true) { token -> api.retract(bookId, token) }
    override suspend fun download(plan: PreparedCommunityDownload) = perform { token -> api.download(plan, token) }
    override suspend fun moderate(bookId: UUID, decision: CommunityModerationDecision) = perform(mutating = true) { token ->
        if (!moderators.verifiedFor(token)) throw CommunityModeratorUnverified()
        api.moderate(bookId, decision, token)
    }

    private suspend fun <T> perform(mutating: Boolean = false, request: suspend (CommunityUserToken) -> T): CommunityEdgeResult<T> {
        var started = false
        var attemptedToken: String? = null
        return try {
            val raw = session.accessToken() ?: return CommunityEdgeResult.Failure(CommunityEdgeFailureCode.AuthenticationRequired)
            val token = try { CommunityUserToken(raw) } catch (_: IllegalArgumentException) {
                withContext(NonCancellable) { session.invalidate(raw) }
                return CommunityEdgeResult.Failure(CommunityEdgeFailureCode.AuthenticationRequired)
            }
            attemptedToken = raw
            started = true
            val value = request(token)
            var delivered = false
            try {
                // Do not expose a response from a session that expired, rotated or signed out in flight.
                if (!session.isCurrent(raw)) return CommunityEdgeResult.Failure(
                    CommunityEdgeFailureCode.AuthenticationRequired, outcomeMayHaveChanged = mutating)
                delivered = true
                CommunityEdgeResult.Success(value)
            } finally {
                if (!delivered && value is AutoCloseable) value.close()
            }
        } catch (error: CancellationException) { throw error }
        catch (_: CommunityConsentRequired) { CommunityEdgeResult.Failure(CommunityEdgeFailureCode.ConsentRequired) }
        catch (_: CommunityModeratorUnverified) { CommunityEdgeResult.Failure(CommunityEdgeFailureCode.ModeratorNotVerified) }
        catch (_: CommunityEdgeDisabled) { CommunityEdgeResult.Failure(CommunityEdgeFailureCode.Disabled) }
        catch (_: AuthStorageException) { CommunityEdgeResult.Failure(CommunityEdgeFailureCode.StorageError) }
        catch (error: CommunityEdgeHttpException) {
            if (error.status == 401) {
                try { withContext(NonCancellable) { attemptedToken?.let { session.invalidate(it) } } }
                catch (_: Exception) { return CommunityEdgeResult.Failure(CommunityEdgeFailureCode.StorageError) }
            }
            val code = when (error.status) {
                401 -> CommunityEdgeFailureCode.AuthenticationRequired
                403 -> CommunityEdgeFailureCode.Forbidden
                404 -> CommunityEdgeFailureCode.NotFound
                409 -> CommunityEdgeFailureCode.Conflict
                400, 413, 422 -> CommunityEdgeFailureCode.InvalidInput
                429 -> CommunityEdgeFailureCode.RateLimited
                503 -> CommunityEdgeFailureCode.Unavailable
                in 500..599 -> CommunityEdgeFailureCode.ServerError
                else -> CommunityEdgeFailureCode.InvalidResponse
            }
            CommunityEdgeResult.Failure(code, if (error.status == 429) error.retryAfterSeconds else null,
                outcomeMayHaveChanged = mutating && started && (error.status >= 500 || code == CommunityEdgeFailureCode.InvalidResponse))
        } catch (_: InvalidCommunityEdgeResponse) {
            CommunityEdgeResult.Failure(CommunityEdgeFailureCode.InvalidResponse, outcomeMayHaveChanged = mutating && started)
        } catch (_: IllegalArgumentException) {
            CommunityEdgeResult.Failure(CommunityEdgeFailureCode.InvalidInput)
        } catch (_: IOException) {
            CommunityEdgeResult.Failure(CommunityEdgeFailureCode.NetworkUnavailable, outcomeMayHaveChanged = mutating && started)
        } catch (_: Exception) {
            CommunityEdgeResult.Failure(CommunityEdgeFailureCode.InvalidResponse, outcomeMayHaveChanged = mutating && started)
        }
    }
}

/** Application surface. Every method returns Disabled before reading the session or initializing HTTP. */
class CommunityEdgeRepository private constructor(config: CommunityConfiguration, session: CommunitySessionProvider) : CommunityUserOperations {
    private val config = config
    private val core by lazy { CommunityEdgeOperationsCore(GuardedCommunityEdgeApi(config), session) }
    private suspend fun <T> guarded(operation: suspend () -> CommunityEdgeResult<T>): CommunityEdgeResult<T> =
        if (!CommunityEdgeSafety.enabled(config)) CommunityEdgeResult.Failure(CommunityEdgeFailureCode.Disabled) else operation()
    override suspend fun createUpload(input: CreateCommunityUploadInput) = guarded { core.createUpload(input) }
    override suspend fun finalizeUpload(bookId: UUID) = guarded { core.finalizeUpload(bookId) }
    override suspend fun retract(bookId: UUID) = guarded { core.retract(bookId) }
    override suspend fun download(plan: PreparedCommunityDownload) = guarded { core.download(plan) }
    companion object {
        fun fromBuild(session: CommunitySessionProvider) = CommunityEdgeRepository(CommunityConfiguration.fromBuild(), session)
    }
}

/** Separate administrative surface; no user UI action or local role grants access. */
class CommunityModerationRepository private constructor(session: CommunitySessionProvider) : CommunityModeratorOperations {
    private val config = CommunityConfiguration.fromBuild()
    private val core by lazy { CommunityEdgeOperationsCore(GuardedCommunityEdgeApi(config), session, DeniedCommunityModeration) }
    override suspend fun moderate(bookId: UUID, decision: CommunityModerationDecision): CommunityEdgeResult<CommunityModeration> =
        if (!CommunityEdgeSafety.enabled(config)) CommunityEdgeResult.Failure(CommunityEdgeFailureCode.Disabled) else core.moderate(bookId, decision)
    companion object { fun fromBuild(session: CommunitySessionProvider) = CommunityModerationRepository(session) }
}

private class CommunityConsentRequired : Exception("Fresh explicit consent required")
