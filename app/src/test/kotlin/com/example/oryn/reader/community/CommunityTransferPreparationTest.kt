package com.example.oryn.reader.community

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CommunityTransferPreparationTest {
    private fun draft(name: String = "selected.epub", size: Long = 4096, mime: String = "application/epub+zip") =
        CommunityShareDraft(UUID.randomUUID(), name, size, mime)
    private fun consent(draft: CommunityShareDraft) = CommunityShareConsent(draft.selectionId, true, true)

    @Test fun requiresBothExplicitConfirmationsForThisSelectionAndThisDeclaration() {
        val chosen = draft()
        for (choice in listOf(CommunityShareConsent(chosen.selectionId, false, false),
            CommunityShareConsent(chosen.selectionId, true, false), CommunityShareConsent(chosen.selectionId, false, true),
            CommunityShareConsent(UUID.randomUUID(), true, true), consent(chosen).copy(declarationVersion = "other-version"))) {
            assertEquals(CommunityPreparation.Rejected(CommunityPreparationIssue.ConsentRequired), CommunityTransfers.prepareShare(chosen, choice))
        }
        assertTrue(CommunityTransfers.prepareShare(chosen, consent(chosen)) is CommunityPreparation.Review)
    }

    @Test fun selectingSameFilenameAgainCannotReuseConsent() {
        val first = draft(); val second = draft()
        assertEquals(CommunityPreparation.Rejected(CommunityPreparationIssue.ConsentRequired),
            CommunityTransfers.prepareShare(second, consent(first)))
    }

    @Test fun rejectsOtherFormatsUnknownOrOversizedSizesAndUnsafeNames() {
        for (candidate in listOf(draft("selected.pdf"), draft(size = -1), draft(size = 0),
            draft(size = MAX_COMMUNITY_EPUB_BYTES + 1), draft(mime = "application/zip"),
            draft("../selected.epub"), draft("selected\nepub.epub"), draft("x".repeat(256) + ".epub"))) {
            assertEquals(CommunityPreparation.Rejected(CommunityPreparationIssue.InvalidEpub),
                CommunityTransfers.prepareShare(candidate, consent(candidate)))
        }
        assertTrue(draft(size = MAX_COMMUNITY_EPUB_BYTES).isEligible)
    }

    @Test fun demonstrationAndMissingIntegrityCanNeverBecomeDownloadPlans() {
        val book = CommunityBook(UUID.randomUUID(), "test metadata", "test author", "fr")
        assertEquals(CommunityPreparation.Rejected(CommunityPreparationIssue.DemonstrationOnly),
            CommunityTransfers.prepareDownload(CommunityBookDetails(book.copy(origin = CommunityOrigin.Simulation))))
        assertEquals(CommunityPreparation.Rejected(CommunityPreparationIssue.MissingIntegrity),
            CommunityTransfers.prepareDownload(CommunityBookDetails(book)))
    }

    @Test fun validPlansStillCannotExecuteShareOrDownload() {
        val file = draft()
        val share = (CommunityTransfers.prepareShare(file, consent(file)) as CommunityPreparation.Review).value
        assertEquals(CommunityTransferExecution.Disabled, CommunityTransfers.executeShare(share))
        val metadata = CommunityBookDetails(CommunityBook(UUID.randomUUID(), "test", "author", "en"),
            sizeBytes = 4096, sha256 = "a".repeat(64)) // Test-only digest, no file/URL exists.
        val download = (CommunityTransfers.prepareDownload(metadata) as CommunityPreparation.Review).value
        assertEquals(CommunityTransferExecution.Disabled, CommunityTransfers.executeDownload(download))
        assertFalse(share.toString().contains(file.displayName))
        assertFalse(file.toString().contains(file.displayName))
    }
}
