package com.example.oryn.reader.data.importer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class EpubArchiveEntryNameLimitTest {
    private fun zipWithName(name: String): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.closeEntry()
        }
    }.toByteArray()

    @Test fun longestAllowedNameRemainsReadable() {
        val name = "a".repeat(1024)
        EpubArchiveInputStream(ByteArrayInputStream(zipWithName(name))).use { zip ->
            assertEquals(name, zip.nextEntry?.name)
            assertNull(zip.nextEntry)
        }
    }

    @Test fun oversizedNameIsRejectedBeforeItCanAccumulateInTheNameIndex() {
        EpubArchiveInputStream(ByteArrayInputStream(zipWithName("a".repeat(1025)))).use { zip ->
            assertThrows(IOException::class.java) { zip.nextEntry }
        }
    }

    @Test fun embeddedControlCharactersAreRejected() {
        for (name in listOf("OEBPS/a\u0000.xhtml", "OEBPS/a\n.xhtml", "OEBPS/a\t.xhtml")) {
            EpubArchiveInputStream(ByteArrayInputStream(zipWithName(name))).use { zip ->
                assertThrows(IOException::class.java) { zip.nextEntry }
            }
        }
    }
}
