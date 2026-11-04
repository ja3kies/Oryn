package com.example.oryn.reader.ui.components

import org.junit.Assert.*
import org.junit.Test
import androidx.compose.ui.graphics.luminance
import com.example.oryn.reader.ui.theme.pageColor
import com.example.oryn.reader.ui.theme.textColor

class ReaderControlsStateTest {
    @Test fun singleTapThenMenuThenDismissFollowsTheThreeStates() {
        var state = ReaderControlsState.HIDDEN
        state = state.transition(ReaderControlsEvent.SURFACE_TAP)
        assertEquals(ReaderControlsState.COMPACT, state)
        state = state.transition(ReaderControlsEvent.EXPAND)
        assertEquals(ReaderControlsState.EXPANDED, state)
        assertEquals(ReaderControlsState.COMPACT, state.transition(ReaderControlsEvent.DISMISS_SHEET))
    }
    @Test fun backCollapsesBeforeHidingAndOnlyThenLeavesTheReader() {
        var state = ReaderControlsState.EXPANDED
        assertFalse(state.backLeavesReader)
        state = state.transition(ReaderControlsEvent.BACK)
        assertEquals(ReaderControlsState.COMPACT, state)
        assertFalse(state.backLeavesReader)
        state = state.transition(ReaderControlsEvent.BACK)
        assertEquals(ReaderControlsState.HIDDEN, state)
        assertTrue(state.backLeavesReader)
    }
    @Test fun repeatedExpansionCannotCreateASecondPanel() {
        assertEquals(ReaderControlsState.HIDDEN, ReaderControlsState.HIDDEN.transition(ReaderControlsEvent.EXPAND))
        assertEquals(ReaderControlsState.EXPANDED, ReaderControlsState.EXPANDED.transition(ReaderControlsEvent.EXPAND))
        assertEquals(ReaderControlsState.COMPACT, ReaderControlsState.COMPACT.transition(ReaderControlsEvent.DISMISS_SHEET))
    }
    @Test fun tappingOutsideCompactHidesAndDismissingExpandedReturnsCompact() {
        assertEquals(ReaderControlsState.HIDDEN, ReaderControlsState.COMPACT.transition(ReaderControlsEvent.SURFACE_TAP))
        assertEquals(ReaderControlsState.COMPACT, ReaderControlsState.EXPANDED.transition(ReaderControlsEvent.SURFACE_TAP))
    }
    @Test fun temporaryToolsCanReturnToCompactWithoutTouchingDocumentState() {
        for (state in ReaderControlsState.entries) {
            assertEquals(ReaderControlsState.HIDDEN, state.transition(ReaderControlsEvent.HIDE))
            assertEquals(ReaderControlsState.COMPACT, state.transition(ReaderControlsEvent.SHOW_COMPACT))
        }
    }
    @Test fun chapterProgressUsesVisibleOffsetsAndDistinctBookBounds() {
        val chapters = listOf(100 to "First", 600 to "Second")
        assertNull(readerChapterProgress(chapters, 50, 1000))
        val first = readerChapterProgress(chapters, 350, 1000)!!
        assertEquals("First", first.title)
        assertEquals(0.5f, first.fraction, 0.00001f)
        assertNull(first.remainingPages) // No EPUB page count or speed is inferred from character length.
        assertEquals("Second", readerChapterProgress(chapters, 600, 1000)!!.title)
    }
    @Test fun pdfRemainderIsTheActualNumberOfPagesAfterTheVisiblePage() {
        val chapters = listOf(0 to "First", 300_000 to "Second")
        assertEquals(1, readerChapterProgress(chapters, 100_000, 500_000, 100_000)!!.remainingPages)
        assertEquals(0, readerChapterProgress(chapters, 200_000, 500_000, 100_000)!!.remainingPages)
        assertEquals(1, readerChapterProgress(chapters, 300_000, 500_000, 100_000)!!.remainingPages)
    }
    @Test fun unknownOrInvalidGeometryCannotProduceAChapterEstimate() {
        assertNull(readerChapterProgress(emptyList(), 0, 100))
        assertNull(readerChapterProgress(listOf(0 to "First"), 100, 100))
        assertNull(readerChapterProgress(listOf(0 to "First"), 0, 0))
        assertNull(readerChapterProgress(listOf(0 to "First"), -1, 100))
        assertNull(readerChapterProgress(listOf(200 to "Out of bounds"), 0, 100))
    }
    @Test fun guttersDependOnTypographyNotTheVisibilityState() {
        val normal = readerChromeMetrics(1f)
        assertTrue(normal.top.value >= 48)
        val large = readerChromeMetrics(2f)
        assertTrue(large.progress > normal.progress)
        for (ignored in ReaderControlsState.entries) assertEquals(large, readerChromeMetrics(2f))
    }
    @Test fun epubChapterPagesComeFromActualPageBoundariesAndChangeAfterRepagination() {
        val toc = listOf(0, 600)
        assertEquals(1, readerChapterPagesAfter(intArrayOf(0, 200, 400, 600, 800), 1, toc, 1000))
        assertEquals(0, readerChapterPagesAfter(intArrayOf(0, 200, 400, 600, 800), 2, toc, 1000))
        assertEquals(2, readerChapterPagesAfter(intArrayOf(0, 100, 200, 300, 400, 500, 600, 700, 800, 900), 3, toc, 1000))
        assertNull(readerChapterPagesAfter(intArrayOf(0, 200), 0, listOf(100), 400))
        assertNull(readerChapterPagesAfter(intArrayOf(), 0, toc, 1000))
    }
    @Test fun duplicateChapterOffsetsDoNotHideTheCurrentChapter() {
        val toc = listOf(0 to "Introduction", 0 to "Duplicate", 400 to "Chapter two")
        val first = readerChapterProgress(toc, 200, 800)!!
        assertEquals("Introduction", first.title)
        assertEquals(0.5f, first.fraction, 0.00001f)
        assertEquals("Chapter two", readerChapterProgress(toc, 400, 800)!!.title)
    }
    @Test fun malformedPaginationDoesNotReportMisleadingRemainingPages() {
        val toc = listOf(0, 500)
        assertNull(readerChapterPagesAfter(intArrayOf(0, 300, 200), 1, toc, 800))
        assertNull(readerChapterPagesAfter(intArrayOf(0, 200, 200), 1, toc, 800))
        assertNull(readerChapterPagesAfter(intArrayOf(-1, 200), 1, toc, 800))
        assertNull(readerChapterPagesAfter(intArrayOf(0, 800), 0, toc, 800))
    }
    @Test fun readerThemesKeepReadableTextContrast() {
        for (theme in com.example.oryn.reader.data.local.preferences.ReaderThemePreset.entries) {
            val background = theme.pageColor.luminance()
            val foreground = theme.textColor.luminance()
            val ratio = (maxOf(background, foreground) + 0.05f) / (minOf(background, foreground) + 0.05f)
            assertTrue("Contrast for $theme", ratio >= 4.5f)
        }
    }

}
