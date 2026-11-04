package com.example.oryn.reader.ui.screen

import org.junit.Assert.*
import org.junit.Test

class ReaderPositionTest {
    @Test fun savedPdfPageDoesNotRewindDueToFloatTruncation() {
        // 13f / 22 * 22 is 12.999999f; truncating used to reopen the preceding page.
        assertEquals(12, pdfPageAtProgress(13f / 22, 22))
    }
    @Test fun everyPageRoundTripsAcrossRepresentativeDocumentLengths() {
        for (count in 1..500) for (page in 0 until count) {
            assertEquals("$page/$count", page, pdfPageAtProgress((page + 1f) / count, count))
        }
    }
    @Test fun invalidAndBoundaryProgressStayWithinTheAvailablePages() {
        assertEquals(0, pdfPageAtProgress(Float.NaN, 22))
        assertEquals(0, pdfPageAtProgress(-1f, 22))
        assertEquals(21, pdfPageAtProgress(2f, 22))
        assertEquals(0, pdfPageAtProgress(1f, 0))
    }
}
