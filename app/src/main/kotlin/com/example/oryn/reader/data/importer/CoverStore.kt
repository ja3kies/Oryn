package com.example.oryn.reader.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import java.io.File

/**
 * Enregistre les couvertures dans le stockage privé de l'app (redimensionnées, en JPEG),
 * qu'elles viennent d'un EPUB, d'un téléchargement ou d'une image choisie par l'utilisateur.
 *
 * Les couvertures sont nommées par un hash de contenu stable (`cover_<hash>.jpg`) :
 * cela permet de retrouver la couverture d'un livre supprimé puis réimporté, sans
 * jamais toucher au fichier EPUB source (qui reste immuable).
 */
class CoverStore(private val context: Context) {

    private val dir = File(context.filesDir, "covers").apply { mkdirs() }

    /** Renvoie le chemin du fichier créé, ou null si les octets ne sont pas une image. */
    fun saveBytes(bytes: ByteArray, stableHash: String? = null): String? = runCatching {
        if (bytes.size > MAX_INPUT_BYTES) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return@runCatching null

        var resized = decoded
        var flat: Bitmap? = null
        var temporary: File? = null
        try {
            val ratio = MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
            if (ratio < 1f) {
                resized = Bitmap.createScaledBitmap(
                    decoded, (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1), true
                )
            }

            // Flatten transparency, then release all native bitmap buffers even on write failure.
            val outputBitmap = Bitmap.createBitmap(resized.width, resized.height, Bitmap.Config.ARGB_8888)
            flat = outputBitmap
            Canvas(outputBitmap).apply {
                drawColor(Color.WHITE)
                drawBitmap(resized, 0f, 0f, null)
            }
            val hashPart = stableHash?.takeIf { it.matches(Regex("[a-fA-F0-9]{1,64}")) }?.take(40)
            val prefix = if (hashPart == null) "cover_" else "cover_${hashPart}_"
            val staged = File.createTempFile(prefix, ".tmp", dir)
            temporary = staged
            staged.outputStream().use {
                if (!outputBitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) return@runCatching null
            }
            val file = File(dir, staged.nameWithoutExtension + ".jpg")
            if (file.exists() || !staged.renameTo(file)) return@runCatching null
            file.absolutePath
        } finally {
            temporary?.delete()
            flat?.recycle()
            if (resized !== decoded) resized.recycle()
            decoded.recycle()
        }
    }.getOrNull()

    fun saveUri(uri: Uri, stableHash: String? = null): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_INPUT_BYTES) return@use null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: return@runCatching null
        saveBytes(bytes, stableHash)
    }.getOrNull()

    /**
     * Chemin de la couverture associée à un hash, ou null.
     * Cherche `cover_<hash>_<timestamp>.jpg` (actuel) et
     * `cover_<hash>.jpg` (ancien format, compatibilité).
     */
    fun findByHash(stableHash: String): String? {
        val hashPart = stableHash.take(40)
        val legacyName = "cover_$hashPart.jpg"
        val prefix = "cover_${hashPart}_"
        return dir.listFiles()
            ?.filter { it.isFile && it.extension == "jpg" && (it.name == legacyName || it.name.startsWith(prefix)) }
            ?.maxByOrNull { it.lastModified() }
            ?.absolutePath
    }

    /** Ne supprime que les fichiers créés par ce dossier de couvertures. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (file.parentFile == dir) file.delete()
    }

    private companion object {
        const val MAX_SIDE = 900
        const val MAX_INPUT_BYTES = 15 * 1024 * 1024
    }
}
