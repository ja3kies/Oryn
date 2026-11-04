package com.example.oryn.reader.data.importer

import org.junit.Assert.*
import org.junit.Test

class PdfRenderPolicyTest {
    @Test fun ordinaryPagesUseTheActualViewportAndPreserveAspectRatio() {
        assertEquals(PdfRenderSize(600, 900), pdfRenderSize(400, 600, 600))
        assertEquals(PdfRenderSize(1200, 1800), pdfRenderSize(400, 600, 1200))
    }
    @Test fun extremeAspectRatiosAndLargeScreensCannotCreateUnboundedBitmaps() {
        for ((width, height) in listOf(1 to Int.MAX_VALUE, Int.MAX_VALUE to 1, 600 to 900, 1 to 1)) {
            val size = pdfRenderSize(width, height, Int.MAX_VALUE)!!
            assertTrue(size.width in 1..4096)
            assertTrue(size.height in 1..8192)
            assertTrue(size.width.toLong() * size.height <= 4_000_000)
        }
    }
    @Test fun invalidDimensionsDoNotReachTheNativeRenderer() {
        assertNull(pdfRenderSize(0, 10, 1080))
        assertNull(pdfRenderSize(10, -1, 1080))
        assertNull(pdfRenderSize(10, 10, 0))
    }
}
