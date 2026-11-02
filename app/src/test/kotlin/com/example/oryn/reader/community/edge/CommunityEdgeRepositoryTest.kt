package com.example.oryn.reader.community.edge

import com.example.oryn.reader.community.CommunityConfiguration
import com.example.oryn.reader.community.CommunitySessionProvider
import com.example.oryn.reader.community.auth.AuthApi
import com.example.oryn.reader.community.auth.AuthConfiguration
import com.example.oryn.reader.community.auth.AuthHttpException
import com.example.oryn.reader.community.auth.AuthSession
import com.example.oryn.reader.community.auth.AuthStorageException
import com.example.oryn.reader.community.auth.AuthStore
import com.example.oryn.reader.community.auth.AuthTokens
import com.example.oryn.reader.community.auth.StoredAuth
import com.example.oryn.reader.community.auth.SupabaseAuthController
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.UUID

class CommunityEdgeRepositoryTest {
    private lateinit var fixture: EdgeTestFixture
    @Before fun start() { fixture = EdgeTestFixture() }
    @After fun end() { fixture.close() }

    @Test fun mapsRequiredHttpErrorsWithoutRetryingOrLeakingResponseBodies() = runBlocking {
        val expected = mapOf(401 to CommunityEdgeFailureCode.AuthenticationRequired, 403 to CommunityEdgeFailureCode.Forbidden,
            404 to CommunityEdgeFailureCode.NotFound, 429 to CommunityEdgeFailureCode.RateLimited, 500 to CommunityEdgeFailureCode.ServerError)
        expected.forEach { (status, code) ->
            val session = EdgeSession()
            fixture.server.enqueue(fixture.json("private details", status).setHeader("Retry-After", "60"))
            val result = CommunityEdgeOperationsCore(fixture.protocol, session).finalizeUpload(edgeBookId) as CommunityEdgeResult.Failure
            assertEquals(code, result.code)
            assertEquals(if (status == 429) 60 else null, result.retryAfterSeconds)
            assertEquals(if (status == 401) 1 else 0, session.clears)
            assertEquals(status == 500, result.outcomeMayHaveChanged)
        }
        assertEquals(expected.size, fixture.server.requestCount)
    }

    @Test fun rejectedSessionIsClearedAndTheOperationIsNeverAutomaticallyReplayed() = runBlocking {
        fixture.server.enqueue(fixture.json("invalid_session", 401))
        assertEquals(CommunityEdgeFailureCode.AuthenticationRequired,
            (fixture.repository.retract(edgeBookId) as CommunityEdgeResult.Failure).code)
        assertNull(fixture.session.token)
        assertEquals(1, fixture.session.clears)
        assertEquals(CommunityEdgeFailureCode.AuthenticationRequired,
            (fixture.repository.retract(edgeBookId) as CommunityEdgeResult.Failure).code)
        assertEquals(1, fixture.server.requestCount)
    }

    @Test fun absentSessionOrUnavailableSecureStorageCannotReachEdge() = runBlocking {
        val signedOut = CommunityEdgeOperationsCore(fixture.protocol, EdgeSession(null))
        assertEquals(CommunityEdgeFailureCode.AuthenticationRequired,
            (signedOut.finalizeUpload(edgeBookId) as CommunityEdgeResult.Failure).code)
        val unavailable = object : EdgeSession() {
            override suspend fun accessToken(): String? = throw AuthStorageException()
        }
        assertEquals(CommunityEdgeFailureCode.StorageError,
            (CommunityEdgeOperationsCore(fixture.protocol, unavailable).finalizeUpload(edgeBookId) as CommunityEdgeResult.Failure).code)
        assertEquals(0, fixture.server.requestCount)
    }

    @Test fun sessionTransportFailureIsNotMistakenForAMutationThatReachedServer() = runBlocking {
        val offline = object : EdgeSession() {
            override suspend fun accessToken(): String? = throw IOException("fixture offline")
        }
        val result = CommunityEdgeOperationsCore(fixture.protocol, offline).finalizeUpload(edgeBookId) as CommunityEdgeResult.Failure
        assertEquals(CommunityEdgeFailureCode.NetworkUnavailable, result.code)
        assertFalse(result.outcomeMayHaveChanged)
        assertEquals(0, fixture.server.requestCount)
    }

    @Test fun expiredSessionRefreshesBeforeTheOnlyEdgeRequest() = runBlocking {
        val auth = AuthFixture()
        fixture.server.enqueue(fixture.finalized())
        val core = CommunityEdgeOperationsCore(fixture.protocol, auth.controller)
        assertTrue(core.finalizeUpload(edgeBookId) is CommunityEdgeResult.Success)
        assertEquals(1, auth.refreshes)
        assertTrue(fixture.server.takeRequest().getHeader("Authorization") == "Bearer $freshEdgeToken")
        assertTrue(auth.record!!.session!!.tokens.access == freshEdgeToken)
    }

    @Test fun expiredRefreshRejectedByAuthCannotSendAnOldJwtToEdge() = runBlocking {
        val auth = AuthFixture(rejectRefresh = true)
        val result = CommunityEdgeOperationsCore(fixture.protocol, auth.controller).finalizeUpload(edgeBookId) as CommunityEdgeResult.Failure
        assertEquals(CommunityEdgeFailureCode.AuthenticationRequired, result.code)
        assertNull(auth.record)
        assertEquals(1, auth.refreshes)
        assertEquals(0, fixture.server.requestCount)
    }

    @Test fun moderationHasNoDefaultClientGrantAndServer403RemainsAuthoritative() = runBlocking {
        assertEquals(CommunityEdgeFailureCode.ModeratorNotVerified,
            (fixture.repository.moderate(edgeBookId, CommunityModerationDecision.Publish) as CommunityEdgeResult.Failure).code)
        assertEquals(0, fixture.server.requestCount)
        // Test-only capability fixture, not a role/JWT metadata decision or a production implementation.
        val capability = CommunityModeratorAuthorization { token -> token.value == edgeToken }
        val moderators = CommunityEdgeOperationsCore(fixture.protocol, fixture.session, capability)
        fixture.server.enqueue(fixture.json("moderator_access_required", 403))
        assertEquals(CommunityEdgeFailureCode.Forbidden,
            (moderators.moderate(edgeBookId, CommunityModerationDecision.Publish) as CommunityEdgeResult.Failure).code)
        assertEquals(0, fixture.session.clears)
        val request = fixture.server.takeRequest()
        assertEquals("/functions/v1/catalog-moderate", request.path)
        val body = org.json.JSONObject(request.body.readUtf8())
        assertEquals(setOf("bookId", "decision"), body.keys().asSequence().toSet())
        assertEquals("publish", body.getString("decision"))
        fixture.server.enqueue(fixture.json("""{"bookId":"$edgeBookId","status":"rejected","publishedAt":null}"""))
        val result = moderators.moderate(edgeBookId, CommunityModerationDecision.Reject) as CommunityEdgeResult.Success
        assertEquals(CommunityModerationDecision.Reject, result.value.decision)
    }

    @Test fun applicationRepositoriesAndDirectApiStayDisabledBeforeAuth() = runBlocking {
        val forbiddenSession = object : CommunitySessionProvider {
            override suspend fun accessToken(): String? = error("Application gate must precede Auth")
            override suspend fun clear() = error("Application gate must not change Auth")
        }
        val app = CommunityEdgeRepository.fromBuild(forbiddenSession)
        val results = listOf(app.createUpload(createInput()), app.finalizeUpload(edgeBookId), app.retract(edgeBookId), app.download(downloadPlan()),
            CommunityModerationRepository.fromBuild(forbiddenSession).moderate(edgeBookId, CommunityModerationDecision.Publish))
        results.forEach { assertEquals(CommunityEdgeFailureCode.Disabled, (it as CommunityEdgeResult.Failure).code) }
        val api = GuardedCommunityEdgeApi(CommunityConfiguration("https://isolated.example.test", edgeKey))
        val token = CommunityUserToken(edgeToken)
        for (call in listOf<suspend () -> Any>({ api.create(createInput(), token) }, { api.finalize(edgeBookId, token) },
            { api.retract(edgeBookId, token) }, { api.download(downloadPlan(), token) }, { api.moderate(edgeBookId, CommunityModerationDecision.Reject, token) })) {
            try { call(); fail("Direct API bypassed community gate") } catch (_: CommunityEdgeDisabled) { }
        }
        assertEquals(0, fixture.server.requestCount)
    }

    @Test fun cleanupIsAbsentFromAndroidSurfaces() {
        assertEquals(setOf("createUpload", "finalizeUpload", "retract", "download"), CommunityUserOperations::class.java.declaredMethods.map { it.name }.toSet())
        assertEquals(setOf("moderate"), CommunityModeratorOperations::class.java.declaredMethods.map { it.name }.toSet())
        assertFalse(UserCommunityFunction.entries.any { it.path.contains("cleanup") })
    }

    @Test fun delayed401CannotEraseASessionRefreshedByAnotherOperation() = runBlocking {
        val auth = AuthFixture()
        auth.time = 1000
        // The restored old token is valid at this time; use a verifier that accepts either fixture token.
        val api = object : AuthApi {
            override suspend fun exchange(code: String, verifier: String): AuthTokens = error("unused")
            override suspend fun refresh(refreshToken: String) = AuthTokens(freshEdgeToken, "rotated-refresh", 3600)
            override suspend fun userId(accessToken: String) = edgeOwnerId
            override suspend fun logout(accessToken: String) = Unit
        }
        val config = AuthConfiguration("https://isolated.example.test", edgeKey, "reader.example.test", "github", true, true, true)
        val vault = object : AuthStore {
            override suspend fun read() = auth.record
            override suspend fun write(value: StoredAuth) { auth.record = value }
            override suspend fun clear() { auth.record = null }
        }
        val controller = SupabaseAuthController(config, api, vault) { auth.time }
        val core = CommunityEdgeOperationsCore(fixture.protocol, controller)
        fixture.server.enqueue(fixture.json("expired_old_token", 401).setHeadersDelay(1, java.util.concurrent.TimeUnit.SECONDS))
        val pending = async(start = CoroutineStart.UNDISPATCHED) { core.finalizeUpload(edgeBookId) }
        val request = fixture.server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!
        assertTrue(request.getHeader("Authorization") == "Bearer $edgeToken")
        auth.time = 4000
        assertTrue(controller.accessToken() == freshEdgeToken)
        assertEquals(CommunityEdgeFailureCode.AuthenticationRequired, (pending.await() as CommunityEdgeResult.Failure).code)
        assertNotNull(auth.record)
        assertTrue(controller.accessToken() == freshEdgeToken)
        assertEquals(1, fixture.server.requestCount)
    }

    @Test fun projectApiKeyCanNeverBeUsedAsTheUserBearer() = runBlocking {
        val keySession = EdgeSession(edgeKey)
        val result = CommunityEdgeOperationsCore(fixture.protocol, keySession).finalizeUpload(edgeBookId) as CommunityEdgeResult.Failure
        assertEquals(CommunityEdgeFailureCode.AuthenticationRequired, result.code)
        assertEquals(1, keySession.clears)
        assertEquals(0, fixture.server.requestCount)
    }

    @Test fun logoutDuringHttpDiscardsLateSuccessWithoutRestoringSession() = runBlocking {
        val auth = AuthFixture()
        val core = CommunityEdgeOperationsCore(fixture.protocol, auth.controller)
        fixture.server.enqueue(fixture.finalized().setHeadersDelay(1, java.util.concurrent.TimeUnit.SECONDS))
        val pending = async(start = CoroutineStart.UNDISPATCHED) { core.finalizeUpload(edgeBookId) }
        assertNotNull(fixture.server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS))
        auth.controller.signOut()
        val result = pending.await() as CommunityEdgeResult.Failure
        assertEquals(CommunityEdgeFailureCode.AuthenticationRequired, result.code)
        assertTrue(result.outcomeMayHaveChanged) // Server may have finalized; never automatically replay.
        assertNull(auth.record)
        assertNull(auth.controller.accessToken())
        assertEquals(1, fixture.server.requestCount)
    }

    @Test fun sessionExpiringDuringHttpCannotDeliverSuccessOrRefreshAndReplay() = runBlocking {
        val auth = AuthFixture()
        val core = CommunityEdgeOperationsCore(fixture.protocol, auth.controller)
        fixture.server.enqueue(fixture.finalized().setHeadersDelay(1, java.util.concurrent.TimeUnit.SECONDS))
        val pending = async(start = CoroutineStart.UNDISPATCHED) { core.finalizeUpload(edgeBookId) }
        assertNotNull(fixture.server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS))
        auth.time = 8000
        assertEquals(CommunityEdgeFailureCode.AuthenticationRequired, (pending.await() as CommunityEdgeResult.Failure).code)
        assertEquals(1, auth.refreshes)
        assertEquals(1, fixture.server.requestCount)
    }

    @Test fun lateDownloadAfterSessionChangeIsZeroedAndNeverDelivered() = runBlocking {
        var captured: IntegrityCheckedCommunityEpub? = null
        val observing = object : CommunityEdgeApi by fixture.protocol {
            override suspend fun download(plan: com.example.oryn.reader.community.PreparedCommunityDownload,
                token: CommunityUserToken): IntegrityCheckedCommunityEpub = fixture.protocol.download(plan, token).also { captured = it }
        }
        val core = CommunityEdgeOperationsCore(observing, fixture.session)
        fixture.server.enqueue(okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "application/epub+zip")
            .setBody(okio.Buffer().write(edgeBytes)).setHeadersDelay(1, java.util.concurrent.TimeUnit.SECONDS))
        val pending = async(start = CoroutineStart.UNDISPATCHED) { core.download(downloadPlan()) }
        assertNotNull(fixture.server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS))
        fixture.session.token = freshEdgeToken
        val result = pending.await() as CommunityEdgeResult.Failure
        assertEquals(CommunityEdgeFailureCode.AuthenticationRequired, result.code)
        assertFalse(result.outcomeMayHaveChanged)
        assertArrayEquals(ByteArray(edgeBytes.size), captured!!.copyBytes())
        assertEquals(freshEdgeToken, fixture.session.token)
        assertEquals(0, fixture.session.clears)
    }

    @Test fun responseValidationFailureAlsoDisposesDownloadedBytes() = runBlocking {
        val content = IntegrityCheckedCommunityEpub(edgeBookId, edgeBytes.copyOf())
        val api = object : CommunityEdgeApi by fixture.protocol {
            override suspend fun download(plan: com.example.oryn.reader.community.PreparedCommunityDownload,
                token: CommunityUserToken) = content
        }
        val storageFailure = object : EdgeSession() {
            override suspend fun isCurrent(accessToken: String): Boolean = throw AuthStorageException()
        }
        val result = CommunityEdgeOperationsCore(api, storageFailure).download(downloadPlan()) as CommunityEdgeResult.Failure
        assertEquals(CommunityEdgeFailureCode.StorageError, result.code)
        assertArrayEquals(ByteArray(edgeBytes.size), content.copyBytes())
    }

    @Test fun revokedConsentNeverCreatesAnEdgeRequest() = runBlocking {
        val input = createInput()
        input.share.revoke()
        val result = fixture.repository.createUpload(input) as CommunityEdgeResult.Failure
        assertEquals(CommunityEdgeFailureCode.ConsentRequired, result.code)
        assertFalse(result.outcomeMayHaveChanged)
        assertEquals(0, fixture.server.requestCount)
    }

    @Test fun revocationWhileAuthWaitsPreventsCreation() = runBlocking {
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val proceed = kotlinx.coroutines.CompletableDeferred<Unit>()
        val waiting = object : EdgeSession() {
            override suspend fun accessToken(): String? { entered.complete(Unit); proceed.await(); return token }
        }
        val input = createInput()
        val pending = async { CommunityEdgeOperationsCore(fixture.protocol, waiting).createUpload(input) }
        entered.await()
        input.share.revoke()
        proceed.complete(Unit)
        assertEquals(CommunityEdgeFailureCode.ConsentRequired, (pending.await() as CommunityEdgeResult.Failure).code)
        assertEquals(0, fixture.server.requestCount)
    }

    @Test fun concurrentCreationConsumesOneConsentAndDoesNotUploadOrFinalize() = runBlocking {
        val input = createInput()
        fixture.server.enqueue(fixture.json("""{"bookId":"$edgeBookId","bucket":"community-epubs","objectPath":"$edgeOwnerId/$edgeBookId.epub","uploadExpiresAt":"1970-01-01T00:10:00Z","maxFileBytes":8388608,"status":"pending"}""", 201)
            .setHeadersDelay(1, java.util.concurrent.TimeUnit.SECONDS))
        val first = async(start = CoroutineStart.UNDISPATCHED) { fixture.repository.createUpload(input) }
        assertNotNull(fixture.server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(CommunityEdgeFailureCode.ConsentRequired,
            (fixture.repository.createUpload(input) as CommunityEdgeResult.Failure).code)
        assertTrue(first.await() is CommunityEdgeResult.Success)
        assertEquals(1, fixture.server.requestCount)
    }

    @Test fun uncertainCreationFailureRequiresFreshConsentAndNeverRetriesAutomatically() = runBlocking {
        val input = createInput()
        fixture.server.enqueue(fixture.json("unavailable", 500))
        val first = fixture.repository.createUpload(input) as CommunityEdgeResult.Failure
        assertEquals(CommunityEdgeFailureCode.ServerError, first.code)
        assertTrue(first.outcomeMayHaveChanged)
        assertEquals(CommunityEdgeFailureCode.ConsentRequired,
            (fixture.repository.createUpload(input) as CommunityEdgeResult.Failure).code)
        assertEquals(1, fixture.server.requestCount)
    }

    @Test fun providerWithoutAtomicCurrentSessionCheckFailsClosed() = runBlocking {
        val unknown = object : CommunitySessionProvider {
            override suspend fun accessToken() = edgeToken
            override suspend fun clear() = Unit
        }
        fixture.server.enqueue(fixture.finalized())
        val result = CommunityEdgeOperationsCore(fixture.protocol, unknown).finalizeUpload(edgeBookId) as CommunityEdgeResult.Failure
        assertEquals(CommunityEdgeFailureCode.AuthenticationRequired, result.code)
        assertTrue(result.outcomeMayHaveChanged)
        assertEquals(1, fixture.server.requestCount)
    }

    @Test fun disabledApplicationDoesNotConsumeConsentOrReadAuth() = runBlocking {
        val input = createInput()
        assertEquals(CommunityEdgeFailureCode.Disabled,
            (CommunityEdgeRepository.fromBuild(fixture.session).createUpload(input) as CommunityEdgeResult.Failure).code)
        assertTrue(input.share.isAvailable)
        assertEquals(0, fixture.session.reads)
        assertEquals(0, fixture.server.requestCount)
    }

    private class AuthFixture(private val rejectRefresh: Boolean = false) {
        private val config = AuthConfiguration("https://isolated.example.test", edgeKey, "reader.example.test", "github", true, true, true)
        var refreshes = 0
        var time = 4000L
        var record: StoredAuth? = StoredAuth(config.binding, AuthSession(AuthTokens(edgeToken, "test-refresh", 3600), edgeOwnerId, 1, 3601))
        private val store = object : AuthStore {
            override suspend fun read() = record
            override suspend fun write(value: StoredAuth) { record = value }
            override suspend fun clear() { record = null }
        }
        private val api = object : AuthApi {
            override suspend fun exchange(code: String, verifier: String): AuthTokens = error("Not a login test")
            override suspend fun refresh(refreshToken: String): AuthTokens {
                refreshes++
                if (rejectRefresh) throw AuthHttpException(401)
                return AuthTokens(freshEdgeToken, "test-rotated-refresh", 3600)
            }
            override suspend fun userId(accessToken: String): UUID { assertTrue(accessToken == freshEdgeToken); return edgeOwnerId }
            override suspend fun logout(accessToken: String) = Unit
        }
        val controller = SupabaseAuthController(config, api, store) { time }
    }
}
