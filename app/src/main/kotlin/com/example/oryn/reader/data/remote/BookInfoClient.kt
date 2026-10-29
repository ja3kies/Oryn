package com.example.oryn.reader.data.remote

import android.content.Context
import android.net.Uri
import androidx.core.text.HtmlCompat
import com.example.oryn.reader.data.importer.BookMetadataReader
import com.example.oryn.reader.data.local.entity.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

/** Informations affichées dans la fiche d'un livre. */
data class BookInfo(
    val description: String? = null,
    val descriptionSource: String? = null,
    val rating: Float? = null,
    val ratingCount: Int = 0,
    val year: Int? = null
)

/**
 * Fiche d'un livre : résumé lu dans l'EPUB lui-même, sinon (et pour la note des lecteurs)
 * Open Library. Le résultat est gardé sur l'appareil pour ne pas redemander à chaque ouverture.
 */
object BookInfoClient {
    private const val CACHE_DAYS = 30L

    suspend fun load(context: Context, book: BookEntity): BookInfo = withContext(Dispatchers.IO) {
        val cacheFile = File(File(context.filesDir, "info").apply { mkdirs() }, "${book.id}.json")
        readCache(cacheFile)?.let { return@withContext it }

        // 1. Résumé intégré à l'EPUB
        var description: String? = null
        var source: String? = null
        if (book.format == "EPUB") {
            description = runCatching { BookMetadataReader(context).readDescription(Uri.parse(book.filePath)) }.getOrNull()
            if (description != null) source = "Résumé de l'éditeur (dans l'EPUB)"
        }

        // 2. Open Library : note, année, résumé de secours
        var rating: Float? = null
        var count = 0
        var year: Int? = null
        var reachable = false
        runCatching {
            val title = URLEncoder.encode(book.title, "UTF-8")
            val author = book.author.takeIf { !it.equals("Auteur inconnu", true) }
                ?.let { "&author=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
            val search = JSONObject(
                get("https://openlibrary.org/search.json?title=$title$author&limit=3" +
                    "&fields=key,title,first_publish_year,ratings_average,ratings_count")
            )
            reachable = true
            val docs = search.optJSONArray("docs")
            val wanted = norm(book.title)
            if (docs != null) {
                for (i in 0 until docs.length()) {
                    val doc = docs.getJSONObject(i)
                    val found = norm(doc.optString("title"))
                    if (found.isBlank() || !(found.contains(wanted) || wanted.contains(found))) continue
                    if (doc.has("ratings_average")) rating = doc.optDouble("ratings_average").toFloat()
                    count = doc.optInt("ratings_count", 0)
                    if (doc.has("first_publish_year")) year = doc.optInt("first_publish_year")
                    if (description == null) {
                        val key = doc.optString("key")
                        if (key.isNotBlank()) {
                            val work = JSONObject(get("https://openlibrary.org$key.json"))
                            val raw = when (val d = work.opt("description")) {
                                is JSONObject -> d.optString("value")
                                is String -> d
                                else -> null
                            }
                            description = raw?.let(::clean)?.takeIf { it.isNotBlank() }
                            if (description != null) source = "Open Library"
                        }
                    }
                    break
                }
            }
        }

        val info = BookInfo(description, source, rating?.takeIf { it > 0f }, count, year)
        // On ne garde en cache que si le réseau a répondu (sinon on réessaiera la prochaine fois)
        if (reachable || description != null) writeCache(cacheFile, info)
        info
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 12_000
        c.setRequestProperty("User-Agent", "OrynReader/1.0 (Android)")
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun clean(raw: String): String =
        HtmlCompat.fromHtml(raw.substringBefore("\n----------").substringBefore("\r\n----------"), HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString().trim()

    private fun norm(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun readCache(file: File): BookInfo? = runCatching {
        if (!file.exists() || System.currentTimeMillis() - file.lastModified() > CACHE_DAYS * 86_400_000L) return null
        val j = JSONObject(file.readText())
        BookInfo(
            description = j.optString("d").takeIf { it.isNotBlank() },
            descriptionSource = j.optString("s").takeIf { it.isNotBlank() },
            rating = if (j.has("r")) j.optDouble("r").toFloat() else null,
            ratingCount = j.optInt("c", 0),
            year = if (j.has("y")) j.optInt("y") else null
        )
    }.getOrNull()

    private fun writeCache(file: File, info: BookInfo) {
        runCatching {
            val j = JSONObject()
            info.description?.let { j.put("d", it) }
            info.descriptionSource?.let { j.put("s", it) }
            info.rating?.let { j.put("r", it.toDouble()) }
            j.put("c", info.ratingCount)
            info.year?.let { j.put("y", it) }
            file.writeText(j.toString())
        }
    }
}
