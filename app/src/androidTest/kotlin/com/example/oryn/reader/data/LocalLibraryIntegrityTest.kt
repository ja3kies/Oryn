package com.example.oryn.reader.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.oryn.reader.data.local.OrynDatabase
import com.example.oryn.reader.data.local.entity.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LocalLibraryIntegrityTest {
    private lateinit var database: OrynDatabase
    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, OrynDatabase::class.java
        ).build()
    }
    @After fun close() { database.close() }

    @Test fun overlappingImportsInsertOneBookWithoutReplacingItsProgress() = runBlocking {
        val dao = database.bookDao()
        val original = BookEntity(title = "Book", author = "Author", filePath = "content://books/1", format = "EPUB")
        val results = coroutineScope {
            (1..12).map { async(Dispatchers.IO) { dao.insertIfAbsent(original) } }.awaitAll()
        }
        assertEquals(1, results.count { it })
        val book = dao.getAllBooks().first().single()
        dao.updateProgress(book.id, 0.75f, 123L)
        assertFalse(dao.insertIfAbsent(original.copy(title = "Replacement")))
        val restored = dao.getAllBooks().first().single()
        assertEquals("Book", restored.title)
        assertEquals(0.75f, restored.progress, 0f)
    }

    @Test fun favoriteToggleAndPathRepairPreserveOtherColumns() = runBlocking {
        val dao = database.bookDao()
        dao.insertIfAbsent(BookEntity(title = "Book", author = "Author", filePath = "content://old", format = "PDF",
            progress = 0.42f, coverPath = "/cover.jpg", contentHash = "hash"))
        val initial = dao.getAllBooks().first().single()
        dao.toggleFavorite(initial.id)
        dao.toggleFavorite(initial.id)
        assertFalse(dao.getAllBooks().first().single().isFavorite)
        dao.toggleFavorite(initial.id)
        dao.updateFilePath(initial.id, "content://repaired")
        val repaired = dao.getAllBooks().first().single()
        assertTrue(repaired.isFavorite)
        assertEquals(initial.progress, repaired.progress, 0f)
        assertEquals(initial.coverPath, repaired.coverPath)
        assertEquals(initial.contentHash, repaired.contentHash)
        assertEquals("content://repaired", repaired.filePath)
    }

    @Test fun sharedCoverReferencesRemainVisibleUntilEveryBookStopsUsingIt() = runBlocking {
        val dao = database.bookDao()
        for (path in listOf("content://a", "content://b")) {
            dao.insertIfAbsent(BookEntity(title = "Book", author = "Author", filePath = path, format = "EPUB", coverPath = "/cover.jpg"))
        }
        assertEquals(2, dao.countByCoverPath("/cover.jpg"))
        dao.updateCover(dao.getAllBooks().first().first().id, "")
        assertEquals(1, dao.countByCoverPath("/cover.jpg"))
    }
}
