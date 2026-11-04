package com.example.oryn.reader.data.importer

import org.junit.Assert.*
import org.junit.Test

class BookFileSignatureTest {
    @Test fun renamedHtmlOrZipIsNotAcceptedAsPdf() {
        assertFalse(BookFileSignature.matches("PDF", "<html>not a PDF</html>".toByteArray()))
        assertFalse(BookFileSignature.matches("PDF", byteArrayOf(80, 75, 3, 4)))
        assertTrue(BookFileSignature.matches("PDF", "%PDF-1.7\n".toByteArray()))
        assertTrue(BookFileSignature.matches("PDF", "\n%PDF-2.0\n".toByteArray()))
    }

    @Test fun epubRequiresLocalZipEntrySignature() {
        assertTrue(BookFileSignature.matches("EPUB", byteArrayOf(80, 75, 3, 4)))
        assertFalse(BookFileSignature.matches("EPUB", byteArrayOf(80, 75, 5, 6)))
        assertFalse(BookFileSignature.matches("EPUB", "Not an EPUB".toByteArray()))
        assertFalse(BookFileSignature.matches("EPUB", byteArrayOf(80, 75)))
    }

    @Test fun emptyUnreadableAndOversizedPrefixesAreNeverValidImports() {
        for (format in listOf("PDF", "EPUB", "TXT", "HTML")) {
            assertFalse(BookFileSignature.matches(format, byteArrayOf()))
            assertFalse(BookFileSignature.matches(format, ByteArray(BookFileSignature.PREFIX_BYTES + 1)))
        }
        assertFalse(BookFileSignature.matches("EXE", "book".toByteArray()))
    }

    @Test fun unicodeTextAndHtmlRemainSupportedWithoutAsciiMagic() {
        assertTrue(BookFileSignature.matches("TXT", "你好 🌍".toByteArray()))
        assertTrue(BookFileSignature.matches("HTML", "<!DOCTYPE html><html>livre</html>".toByteArray()))
    }
}
