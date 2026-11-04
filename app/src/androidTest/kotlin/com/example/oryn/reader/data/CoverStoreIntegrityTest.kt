package com.example.oryn.reader.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.oryn.reader.data.importer.CoverStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class CoverStoreIntegrityTest {
    private lateinit var directory: File
    private lateinit var store: CoverStore
    private val hash = "a".repeat(64)

    @Before fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File.createTempFile("cover-test-", "", context.cacheDir).apply { delete(); mkdir() }
        store = CoverStore(object : ContextWrapper(context) { override fun getFilesDir() = directory })
    }
    @After fun close() { directory.deleteRecursively() }

    @Test fun repeatedSavesProduceCompleteDistinctImagesAndNoTemporaryFiles() {
        val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)
        val bytes = try {
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } finally { bitmap.recycle() }
        val first = store.saveBytes(bytes, hash)
        val second = store.saveBytes(bytes, hash)
        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second)
        val decoded = BitmapFactory.decodeFile(second)
        assertNotNull(decoded)
        decoded?.recycle()
        assertFalse(File(directory, "covers").listFiles().orEmpty().any { it.extension == "tmp" })
        assertNull(store.saveBytes(byteArrayOf(1, 2, 3), hash))
    }

    @Test fun newestCompletedCoverWinsOverLegacyAndUnfinishedFiles() {
        val covers = File(directory, "covers")
        val prefix = "cover_${hash.take(40)}"
        File(covers, "$prefix.jpg").apply { writeText("legacy"); setLastModified(1000) }
        val newest = File(covers, "${prefix}_updated.jpg").apply { writeText("new"); setLastModified(2000) }
        File(covers, "${prefix}_unfinished.tmp").apply { writeText("partial"); setLastModified(3000) }
        assertEquals(newest.absolutePath, store.findByHash(hash))
    }
}
