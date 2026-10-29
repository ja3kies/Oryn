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
 */
class CoverStore(private val context: Context) {

    private val dir = File(context.filesDir, "covers").apply { mkdirs() }

    /** Renvoie le chemin du fichier créé, ou null si les octets ne sont pas une image. */
    fun saveBytes(bytes: ByteArray): String? = runCatching {
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

        val ratio = MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val resized = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                decoded, (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1), true
            )
        } else decoded

        // Fond blanc : une image transparente ne devient pas noire en JPEG
        val flat = Bitmap.createBitmap(resized.width, resized.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(resized, 0f, 0f, null)
        }

        val file = File(dir, "cover_${System.currentTimeMillis()}_${(0..9999).random()}.jpg")
        file.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        file.absolutePath
    }.getOrNull()

    fun saveUri(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let(::saveBytes)
    }.getOrNull()

    /** Ne supprime que les fichiers créés par ce dossier de couvertures. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (file.parentFile == dir) file.delete()
    }

    private companion object {
        const val MAX_SIDE = 900
    }
}

