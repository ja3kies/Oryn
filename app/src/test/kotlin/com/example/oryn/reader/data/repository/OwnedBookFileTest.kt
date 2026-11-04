package com.example.oryn.reader.data.repository

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class OwnedBookFileTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun downloadedAndSharedFilesCanBeCleanedButExternalFilesCannot() {
        val books = temporary.newFolder("books")
        val downloaded = File(books, "book.epub").apply { writeText("book") }
        val shared = File(books, "shared").apply { mkdir() }
        val imported = File(shared, "book.pdf").apply { writeText("book") }
        val external = temporary.newFile("external.pdf")
        assertEquals(downloaded.canonicalFile, ownedBookFile(downloaded, books))
        assertEquals(imported.canonicalFile, ownedBookFile(imported, books))
        assertNull(ownedBookFile(external, books))
        assertNull(ownedBookFile(books, books))
        assertNull(ownedBookFile(File(books, "missing.epub"), books))
    }

    @Test fun traversalAndSymbolicLinksCannotEscapeThePrivateBookDirectory() {
        val books = temporary.newFolder("books")
        val external = temporary.newFile("external.pdf")
        assertNull(ownedBookFile(File(books, "../external.pdf"), books))
        val link = File(books, "link.pdf")
        Files.createSymbolicLink(link.toPath(), external.toPath())
        assertNull(ownedBookFile(link, books))
    }
}
