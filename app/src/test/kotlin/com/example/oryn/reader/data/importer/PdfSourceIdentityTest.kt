package com.example.oryn.reader.data.importer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class PdfSourceIdentityTest {
    private fun snapshot(text: String): String = copyPdfSnapshot(ByteArrayInputStream(text.toByteArray()), ByteArrayOutputStream())

    @Test fun sameUriPageCountAndLengthCannotReuseTextFromDifferentPdfBytes() {
        val firstBytes = "%PDF-1.4 first text"
        val secondBytes = "%PDF-1.4 other text"
        assertEquals(firstBytes.toByteArray().size, secondBytes.toByteArray().size)
        val first = snapshot(firstBytes)
        val second = snapshot(secondBytes)
        assertNotEquals(first, second)
        assertNotEquals(pdfReflowCacheIdentity("content://books/7", 22, first), pdfReflowCacheIdentity("content://books/7", 22, second))
    }
    @Test fun identicalSnapshotRetainsItsStableCacheIdentity() {
        assertEquals(pdfReflowCacheIdentity("content://books/7", 22, snapshot("stable bytes")),
            pdfReflowCacheIdentity("content://books/7", 22, snapshot("stable bytes")))
    }
    @Test fun unverifiableSourceIdentitySkipsPersistedCache() {
        assertNull(pdfReflowCacheIdentity("content://books/7", 22, null))
        assertNull(pdfReflowCacheIdentity("content://books/7", 22, "size-and-mtime"))
    }
    @Test fun snapshotHashesExactlyTheBytesDeliveredToPdfBox() {
        val bytes = ByteArray(32769) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        copyPdfSnapshot(ByteArrayInputStream(bytes), output)
        assertArrayEquals(bytes, output.toByteArray())
    }
    @Test(expected = java.io.IOException::class) fun oversizedSnapshotIsRejectedBeforeWritingBeyondTheLimit() {
        copyPdfSnapshot(ByteArrayInputStream(ByteArray(100)), ByteArrayOutputStream(), 99)
    }
    @Test(expected = java.util.concurrent.CancellationException::class) fun cancelledCopyStopsBeforeReadingTheNextBlock() {
        copyPdfSnapshot(ByteArrayInputStream(ByteArray(32769)), ByteArrayOutputStream(), checkCancellation = {
            throw java.util.concurrent.CancellationException()
        })
    }
}
