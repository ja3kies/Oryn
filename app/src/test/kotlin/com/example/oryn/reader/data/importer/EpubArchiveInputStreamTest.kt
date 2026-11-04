package com.example.oryn.reader.data.importer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class EpubArchiveInputStreamTest {
    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)) // DEFLATED with unknown streaming size and data descriptor.
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    @Test fun nestedUnicodeEntriesAndDirectoriesAreReadable() {
        val content = "<p>Résumé 📚</p>".toByteArray()
        EpubArchiveInputStream(ByteArrayInputStream(archive("OEBPS/" to byteArrayOf(), "OEBPS/école.xhtml" to content))).use { zip ->
            assertTrue(zip.nextEntry.isDirectory)
            assertEquals("OEBPS/école.xhtml", zip.nextEntry.name)
            assertArrayEquals(content, zip.readBytes())
            assertNull(zip.nextEntry)
        }
    }

    @Test fun forbiddenEntryPathsAreRejectedBeforeTheirContentIsRead() {
        for (name in listOf("../outside", "a/../../outside", "/absolute", "a\\..\\outside", "C:/outside", "a/../outside")) {
            EpubArchiveInputStream(ByteArrayInputStream(archive(name to "data".toByteArray()))).use { zip ->
                assertThrows("Unsafe path accepted: $name", IOException::class.java) { zip.nextEntry }
            }
        }
    }

    @Test fun unknownEntrySizeCannotBypassDecompressedByteBudget() {
        val bytes = archive("large.xhtml" to ByteArray(8192) { 65 })
        EpubArchiveInputStream(ByteArrayInputStream(bytes), maxEntryBytes = 1024).use { zip ->
            zip.nextEntry
            assertThrows(IOException::class.java) { zip.readBytes() }
        }
    }

    @Test fun advancingPastAnUnreadBombStillEnforcesItsBudget() {
        val bytes = archive("ignored.xhtml" to ByteArray(8192) { 65 }, "next.xhtml" to byteArrayOf(1))
        EpubArchiveInputStream(ByteArrayInputStream(bytes), maxEntryBytes = 1024).use { zip ->
            zip.nextEntry
            assertThrows(IOException::class.java) { zip.nextEntry }
        }
    }

    @Test fun explicitCloseEntryCannotBypassUnreadByteBudget() {
        val bytes = archive("ignored.xhtml" to ByteArray(8192) { 65 })
        EpubArchiveInputStream(ByteArrayInputStream(bytes), maxEntryBytes = 1024).use { zip ->
            zip.nextEntry
            assertThrows(IOException::class.java) { zip.closeEntry() }
        }
    }

    @Test fun skippingCannotBypassEntryBudget() {
        val bytes = archive("large.xhtml" to ByteArray(8192) { 65 })
        EpubArchiveInputStream(ByteArrayInputStream(bytes), maxEntryBytes = 1024).use { zip ->
            zip.nextEntry
            assertThrows(IOException::class.java) { zip.skip(8192) }
        }
    }

    @Test fun individuallySmallEntriesCannotBypassAggregateBudget() {
        val bytes = archive("one" to ByteArray(700), "two" to ByteArray(700))
        EpubArchiveInputStream(ByteArrayInputStream(bytes), maxEntryBytes = 1024, maxTotalBytes = 1000).use { zip ->
            zip.nextEntry
            assertEquals(700, zip.readBytes().size)
            zip.nextEntry
            assertThrows(IOException::class.java) { zip.readBytes() }
        }
    }

    @Test fun excessiveEntryCountFailsEvenForEmptyEntries() {
        val bytes = archive("one" to byteArrayOf(), "two" to byteArrayOf(), "three" to byteArrayOf())
        EpubArchiveInputStream(ByteArrayInputStream(bytes), maxEntries = 2).use { zip ->
            zip.nextEntry
            zip.nextEntry
            assertThrows(IOException::class.java) { zip.nextEntry }
        }
    }

    @Test fun duplicateEntriesCannotShadowEarlierContent() {
        val bytes = archive("a.xhtml" to byteArrayOf(1), "b.xhtml" to byteArrayOf(2))
        val old = "b.xhtml".toByteArray()
        val replacement = "a.xhtml".toByteArray()
        for (index in 0..bytes.size - old.size) {
            if (old.indices.all { bytes[index + it] == old[it] }) replacement.copyInto(bytes, index)
        }
        EpubArchiveInputStream(ByteArrayInputStream(bytes)).use { zip ->
            zip.nextEntry
            assertThrows(IOException::class.java) { zip.nextEntry }
        }
    }

    @Test fun cancellationInterruptsReadsWithoutBeingConvertedToCorruptFileError() {
        val bytes = archive("content.xhtml" to ByteArray(100))
        var cancelled = false
        EpubArchiveInputStream(ByteArrayInputStream(bytes), checkCancelled = {
            if (cancelled) throw CancellationException("test cancellation")
        }).use { zip ->
            zip.nextEntry
            cancelled = true
            assertThrows(CancellationException::class.java) { zip.readBytes() }
        }
    }
}
