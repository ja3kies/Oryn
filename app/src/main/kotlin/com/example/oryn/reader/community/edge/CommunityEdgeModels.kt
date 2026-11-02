package com.example.oryn.reader.community.edge

import com.example.oryn.reader.community.COMMUNITY_RIGHTS_VERSION
import com.example.oryn.reader.community.CommunityPreparation
import com.example.oryn.reader.community.CommunityTransfers
import com.example.oryn.reader.community.MAX_COMMUNITY_EPUB_BYTES
import com.example.oryn.reader.community.PreparedCommunityShare
import java.util.UUID

/** Selection/consent is rechecked locally. Owner, path, status and declaration version are server-owned. */
class CreateCommunityUploadInput(val share: PreparedCommunityShare, title: String, author: String, val language: String) {
    val title = title.trim()
    val author = author.trim()
    init {
        require(this.title.length in 1..200 && this.title.none(Char::isISOControl))
        require(this.author.length in 1..200 && this.author.none(Char::isISOControl))
        require(language.length <= 35 && language.matches(Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*")))
        require(share.isAvailable)
        require(share.consent.declarationVersion == COMMUNITY_RIGHTS_VERSION)
        require(CommunityTransfers.prepareShare(share.draft, share.consent) is CommunityPreparation.Review)
    }
    override fun toString() = "CreateCommunityUploadInput([redacted])"
}

class CommunityUploadTicket internal constructor(
    val bookId: UUID, val objectPath: String, val expiresAtEpochMillis: Long, val maxFileBytes: Long
) { override fun toString() = "CommunityUploadTicket([redacted])" }

data class CommunityFinalization(val bookId: UUID, val sha256: String)
data class CommunityRetraction(val bookId: UUID, val objectDeleted: Boolean, val cleanupPending: Boolean)
enum class CommunityModerationDecision { Publish, Reject }
data class CommunityModeration(val bookId: UUID, val decision: CommunityModerationDecision, val publishedAtEpochMillis: Long?)

/** Hash/size checked bytes only; not a proof of EPUB safety and never an imported/local book. */
class IntegrityCheckedCommunityEpub internal constructor(val bookId: UUID, private val content: ByteArray) : AutoCloseable {
    val sizeBytes: Int get() = content.size
    fun copyBytes(): ByteArray = content.copyOf()
    override fun close() { content.fill(0) }
    override fun toString() = "IntegrityCheckedCommunityEpub([redacted])"
}

enum class CommunityEdgeFailureCode {
    Disabled, AuthenticationRequired, Forbidden, NotFound, RateLimited, Conflict, InvalidInput,
    ServerError, Unavailable, NetworkUnavailable, InvalidResponse, ConsentRequired, StorageError,
    ModeratorNotVerified
}
sealed interface CommunityEdgeResult<out T> {
    data class Success<T>(val value: T) : CommunityEdgeResult<T>
    data class Failure(
        val code: CommunityEdgeFailureCode, val retryAfterSeconds: Int? = null,
        val outcomeMayHaveChanged: Boolean = false
    ) : CommunityEdgeResult<Nothing>
}

/** No cleanup operation: the scheduled server function has no mobile counterpart. */
interface CommunityUserOperations {
    suspend fun createUpload(input: CreateCommunityUploadInput): CommunityEdgeResult<CommunityUploadTicket>
    suspend fun finalizeUpload(bookId: UUID): CommunityEdgeResult<CommunityFinalization>
    suspend fun retract(bookId: UUID): CommunityEdgeResult<CommunityRetraction>
    suspend fun download(plan: com.example.oryn.reader.community.PreparedCommunityDownload): CommunityEdgeResult<IntegrityCheckedCommunityEpub>
}
interface CommunityModeratorOperations {
    suspend fun moderate(bookId: UUID, decision: CommunityModerationDecision): CommunityEdgeResult<CommunityModeration>
}

internal fun requireCommunityBookId(id: UUID) {
    require(id.toString().matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")))
}
internal fun requireDownloadMetadata(size: Long, hash: String) {
    require(size in 1..MAX_COMMUNITY_EPUB_BYTES && hash.matches(Regex("[a-f0-9]{64}")))
}
internal class CommunityEdgeHttpException(val status: Int, val retryAfterSeconds: Int?) : Exception("Community request rejected")
internal class InvalidCommunityEdgeResponse : Exception("Invalid community response")
internal class CommunityEdgeDisabled : Exception("Community operations disabled")

internal class CommunityModeratorUnverified : Exception("Moderator capability unavailable")
