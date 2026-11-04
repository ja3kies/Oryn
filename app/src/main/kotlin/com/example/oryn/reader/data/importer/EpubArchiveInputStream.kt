package com.example.oryn.reader.data.importer

import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Bounds actual inflation, including entries skipped by [getNextEntry] or [closeEntry].
 * Checking only selected chapters is insufficient: ZipInputStream drains ignored entries too.
 * No archive path is ever extracted to disk, and ambiguous/unsafe entry names are rejected.
 */
internal class EpubArchiveInputStream(
    input: InputStream,
    private val maxEntryBytes: Long = 24L * 1024 * 1024,
    private val maxTotalBytes: Long = 192L * 1024 * 1024,
    private val maxEntries: Int = 20_000,
    private val checkCancelled: () -> Unit = {}
) : ZipInputStream(input) {
    private var entryBytes = 0L
    private var totalBytes = 0L
    private val names = HashSet<String>()

    init {
        require(maxEntryBytes > 0 && maxTotalBytes > 0 && maxEntries > 0)
    }

    override fun getNextEntry(): ZipEntry? {
        checkCancelled()
        // Super drains the previous entry through our bounded read override before advancing.
        val entry = super.getNextEntry() ?: return null
        entryBytes = 0
        if (names.size >= maxEntries) throw IOException("EPUB contains too many entries")
        val name = entry.name
        if (!safeName(name) || !names.add(name.removeSuffix("/"))) {
            throw IOException("EPUB contains an unsafe or duplicate entry path")
        }
        if (entry.size > maxEntryBytes) throw IOException("EPUB entry exceeds size limit")
        return entry
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkCancelled()
        val count = super.read(buffer, offset, length)
        if (count > 0) {
            entryBytes += count
            totalBytes += count
            if (entryBytes > maxEntryBytes || totalBytes > maxTotalBytes) {
                throw IOException("EPUB decompression exceeds size limit")
            }
        }
        return count
    }

    private fun safeName(name: String): Boolean {
        if (name.length !in 1..1024 || name.startsWith('/') || name.any { it == '\\' || it == ':' || it.isISOControl() }) return false
        val segments = name.removeSuffix("/").split('/')
        return segments.all { it.isNotEmpty() && it != "." && it != ".." }
    }
}
