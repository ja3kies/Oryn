package com.example.oryn.reader.community

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Local selection metadata only: no URI, file bytes, token or path leaves the picker UI. */
class CommunityShareDraft(val selectionId: UUID, val displayName: String, val sizeBytes: Long, val mime: String) {
    val isEligible: Boolean get() = displayName.length in 6..255 && displayName.endsWith(".epub", true) &&
        displayName.none { it.isISOControl() || it == '/' || it == '\\' } &&
        sizeBytes in 1..MAX_COMMUNITY_EPUB_BYTES && mime == "application/epub+zip"
    override fun toString() = "CommunityShareDraft([redacted])"
}

data class CommunityShareConsent(
    val selectionId: UUID, val rightsConfirmed: Boolean, val sharingConfirmed: Boolean,
    val declarationVersion: String = COMMUNITY_RIGHTS_VERSION
)
const val COMMUNITY_RIGHTS_VERSION = "oryn-epub-rights-v1"

enum class CommunityPreparationIssue { InvalidEpub, ConsentRequired, DemonstrationOnly, MissingIntegrity }
sealed interface CommunityPreparation<out T> {
    data class Rejected(val issue: CommunityPreparationIssue) : CommunityPreparation<Nothing>
    class Review<T>(val value: T) : CommunityPreparation<T>
}
class PreparedCommunityShare internal constructor(val draft: CommunityShareDraft, val consent: CommunityShareConsent) {
    // One explicit confirmation permits at most one creation attempt, including uncertain failures.
    private val state = AtomicInteger(0)
    internal val isAvailable: Boolean get() = state.get() == 0
    internal fun consumeForCreation(): Boolean = state.compareAndSet(0, 1)
    /** Stops an unstarted attempt. An already-started server mutation needs explicit retraction. */
    fun revoke() { state.set(2) }
    override fun toString() = "PreparedCommunityShare([redacted])"
}
class PreparedCommunityDownload internal constructor(val id: UUID, val sizeBytes: Long, val sha256: String)
enum class CommunityTransferExecution { Disabled }

/** Preparation has no side effect. Execution is unconditionally locked, before any Auth read.
 * No HTTP client, scheduling, file I/O or import callback exists in this boundary.
 */
object CommunityTransfers {
    fun prepareShare(draft: CommunityShareDraft, consent: CommunityShareConsent): CommunityPreparation<PreparedCommunityShare> {
        if (!draft.isEligible) return CommunityPreparation.Rejected(CommunityPreparationIssue.InvalidEpub)
        if (consent.selectionId != draft.selectionId || !consent.rightsConfirmed || !consent.sharingConfirmed ||
            consent.declarationVersion != COMMUNITY_RIGHTS_VERSION) return CommunityPreparation.Rejected(CommunityPreparationIssue.ConsentRequired)
        return CommunityPreparation.Review(PreparedCommunityShare(draft, consent))
    }
    fun prepareDownload(details: CommunityBookDetails): CommunityPreparation<PreparedCommunityDownload> {
        if (details.book.origin == CommunityOrigin.Simulation) return CommunityPreparation.Rejected(CommunityPreparationIssue.DemonstrationOnly)
        val size = details.sizeBytes ?: return CommunityPreparation.Rejected(CommunityPreparationIssue.MissingIntegrity)
        val hash = details.sha256 ?: return CommunityPreparation.Rejected(CommunityPreparationIssue.MissingIntegrity)
        return CommunityPreparation.Review(PreparedCommunityDownload(details.book.id, size, hash))
    }
    fun executeShare(plan: PreparedCommunityShare): CommunityTransferExecution = CommunityTransferExecution.Disabled
    fun executeDownload(plan: PreparedCommunityDownload): CommunityTransferExecution = CommunityTransferExecution.Disabled
}
