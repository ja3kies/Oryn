package com.example.oryn.reader.data.remote

import com.example.oryn.reader.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import android.util.Xml
import kotlinx.coroutines.CancellationException
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

data class OnlineBook(
    val id: Long,
    val title: String,
    val author: String,
    val language: String,
    val epubUrl: String,
    val coverUrl: String?
)

data class OnlinePage(val books: List<OnlineBook>, val hasMore: Boolean)

/** Recherche dans le catalogue Project Gutenberg (domaine public) via l'API ouverte Gutendex. */
object GutendexClient {

    private const val BASE = "https://gutendex.com/books"
    private const val USER_AGENT = "OrynReader/1.0 (Android)"
    private const val MAX_API_RESPONSE_BYTES = 2 * 1024 * 1024
    private const val MAX_COVER_BYTES = 15 * 1024 * 1024
    private const val MAX_BOOK_DOWNLOAD_BYTES = 100L * 1024 * 1024

    /** Gutendex d'abord ; s'il est injoignable, repli sur le catalogue OPDS officiel de Gutenberg. */
    suspend fun search(query: String, language: String?, page: Int): OnlinePage =
        try {
            searchGutendex(query, language, page)
        } catch (e: CancellationException) {
            throw e
        } catch (first: Exception) {
            try {
                searchOpds(query, language, page)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                throw first
            }
        }

    private suspend fun searchGutendex(query: String, language: String?, page: Int): OnlinePage =
        withContext(Dispatchers.IO) {
            val params = buildList {
                if (query.isNotBlank()) add("search=" + URLEncoder.encode(query.trim(), "UTF-8"))
                if (!language.isNullOrBlank()) add("languages=$language")
                add("page=$page")
            }.joinToString("&")

            val root = JSONObject(fetchText("$BASE?$params", readTimeoutMs = 20_000))
            val results = root.getJSONArray("results")
            val books = buildList {
                for (i in 0 until results.length()) parse(results.getJSONObject(i))?.let { add(it) }
            }
            OnlinePage(books, hasMore = !root.isNull("next"))
        }

    suspend fun fetchBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = open(url)
        try {
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_COVER_BYTES) throw IOException("Image trop volumineuse")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Télécharge dans un fichier .part puis le renomme : jamais de fichier à moitié écrit. */
    suspend fun download(
        url: String,
        destination: File,
        headers: Map<String, String> = emptyMap()
    ) = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, destination.name + ".part")
        val connection = open(url, headers)
        try {
            if (connection.contentType?.contains("text/html", ignoreCase = true) == true) {
                throw IOException("Réponse inattendue du serveur")
            }
            if (connection.contentLengthLong > MAX_BOOK_DOWNLOAD_BYTES) {
                throw IOException("Livre trop volumineux")
            }
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BOOK_DOWNLOAD_BYTES) throw IOException("Livre trop volumineux")
                        output.write(buffer, 0, count)
                    }
                }
            }
        } catch (error: Exception) {
            temp.delete()
            throw error
        } finally {
            connection.disconnect()
        }
        if (temp.length() < 1024) {
            temp.delete()
            throw IOException("Fichier incomplet")
        }
        publishStagedFile(temp, destination)
    }

    /** Publishes a completed download without deleting a previous valid file on failure. */
    private fun publishStagedFile(temp: File, destination: File) {
        val parent = destination.parentFile ?: throw IOException("Dossier invalide")
        val staged = File(parent, destination.name + ".publish")
        val backup = File(parent, destination.name + ".backup")
        try {
            temp.copyTo(staged, overwrite = true)
            if (backup.exists() && !backup.delete()) throw IOException("Sauvegarde impossible")
            val hadDestination = destination.exists()
            if (hadDestination && !destination.renameTo(backup)) throw IOException("Remplacement impossible")
            if (!staged.renameTo(destination)) {
                if (hadDestination) backup.renameTo(destination)
                throw IOException("Publication impossible")
            }
            if (backup.exists()) backup.delete()
        } catch (error: Exception) {
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            throw error
        } finally {
            staged.delete()
        }
    }

    /**
     * Téléchargement déclenché depuis le navigateur intégré.

     * Accepte les sites HTTPS publics, mais contrôle chaque redirection et borne le flux.
     * Séparé du client Gutenberg afin de ne pas élargir les hôtes autorisés du catalogue.
     */
    suspend fun downloadFromWeb(
        url: String,
        destination: File,
        headers: Map<String, String> = emptyMap()
    ) = withContext(Dispatchers.IO) {
        val parent = destination.parentFile ?: throw IOException("Dossier de destination invalide")
        parent.mkdirs()
        var current = URL(url)
        val temp = File(parent, destination.name + ".part")
        try {
            repeat(6) { attempt ->
                if (current.protocol != "https" || current.host.isBlank() ||
                    current.userInfo != null || current.port !in setOf(-1, 443)
                ) throw IOException("URL HTTPS non autorisée")

                val connection = current.openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 45_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", USER_AGENT)
                headers.forEach { (name, value) ->
                    if (name.matches(Regex("[A-Za-z0-9-]{1,64}")) &&
                        value.length <= 4096 && !value.contains('\\r') && !value.contains('\\n')
                    ) connection.setRequestProperty(name, value)
                }

                try {
                    val code = connection.responseCode
                    if (code in 300..399) {
                        val location = connection.getHeaderField("Location")
                        if (attempt == 5 || location.isNullOrBlank()) {
                            throw IOException("Redirection invalide")
                        }
                        current = URL(current, location)
                        return@repeat
                    }
                    if (code !in 200..299) throw IOException("HTTP $code")
                    if (connection.contentType?.contains("text/html", true) == true) {
                        throw IOException("La réponse est une page HTML, pas un livre")
                    }
                    if (connection.contentLengthLong > MAX_BOOK_DOWNLOAD_BYTES) {
                        throw IOException("Livre trop volumineux")
                    }
                    temp.outputStream().use { output ->
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > MAX_BOOK_DOWNLOAD_BYTES) throw IOException("Livre trop volumineux")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (temp.length() < 1024) throw IOException("Fichier incomplet")
                    publishStagedFile(temp, destination)
                    return@withContext
                } finally {
                    connection.disconnect()
                }
            }
            throw IOException("Trop de redirections")
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    // ── Repli : catalogue OPDS (Atom) du site officiel Project Gutenberg ──
    private const val OPDS = "https://www.gutenberg.org/ebooks/search.opds/"
    private const val OPDS_PAGE = 25

    private suspend fun searchOpds(query: String, language: String?, page: Int): OnlinePage =
        withContext(Dispatchers.IO) {
            suspend fun load(q: String): OnlinePage {
                val params = buildList {
                    if (q.isNotBlank()) add("query=" + URLEncoder.encode(q.trim(), "UTF-8")) else add("sort_order=downloads")
                    add("start_index=${(page - 1) * OPDS_PAGE + 1}")
                }.joinToString("&")
                return parseOpds(fetchText("$OPDS?$params", readTimeoutMs = 25_000))
            }
            val withLanguage = if (!language.isNullOrBlank()) "$query l.$language".trim() else query
            val first = load(withLanguage)
            // Si le filtre de langue ne donne rien, on réessaie sans
            if (first.books.isEmpty() && !language.isNullOrBlank()) load(query) else first
        }

    private val ebookId = Regex("/ebooks/(\\d+)")

    private fun parseOpds(xml: String): OnlinePage {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val books = mutableListOf<OnlineBook>()
        var hasMore = false
        var inEntry = false
        var title = ""
        var author = ""
        var id: Long? = null
        runCatching {
            var type = parser.eventType
            while (type != XmlPullParser.END_DOCUMENT) {
                when (type) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "entry" -> { inEntry = true; title = ""; author = ""; id = null }
                        "title" -> if (inEntry) { title = parser.nextText().trim() }
                        "content" -> if (inEntry) { author = parser.nextText().trim() }
                        "id" -> if (inEntry && id == null) {
                            id = ebookId.find(parser.nextText())?.groupValues?.get(1)?.toLongOrNull()
                        }
                        "link" -> {
                            val rel = parser.getAttributeValue(null, "rel").orEmpty()
                            if (!inEntry && rel == "next") hasMore = true
                            if (inEntry && id == null) {
                                id = ebookId.find(parser.getAttributeValue(null, "href").orEmpty())
                                    ?.groupValues?.get(1)?.toLongOrNull()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "entry") {
                        inEntry = false
                        val bookId = id
                        if (bookId != null && title.isNotBlank()) {
                            books += OnlineBook(
                                id = bookId,
                                title = title,
                                author = flipName(author).ifBlank { "Auteur inconnu" },
                                language = "",
                                epubUrl = "https://www.gutenberg.org/ebooks/$bookId.epub3.images",
                                coverUrl = "https://www.gutenberg.org/cache/epub/$bookId/pg$bookId.cover.medium.jpg"
                            )
                        }
                    }
                }
                type = parser.next()
            }
        }
        if (books.isEmpty()) throw IOException(tr("Catalogue de secours : aucune réponse exploitable"))
        return OnlinePage(books, hasMore)
    }

    private fun fetchText(url: String, readTimeoutMs: Int = 45_000): String {
        val connection = open(url, readTimeoutMs = readTimeoutMs)
        return try {
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_API_RESPONSE_BYTES) throw IOException("Réponse catalogue trop volumineuse")
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, headers: Map<String, String> = emptyMap(), readTimeoutMs: Int = 45_000): HttpURLConnection {
        var current = URL(url)
        repeat(6) { attempt ->
            val host = current.host.lowercase()
            val allowedHost = host == "gutendex.com" || host == "www.gutenberg.org" ||
                host == "gutenberg.org" || host.endsWith(".gutenberg.org")
            if (current.protocol != "https" || !allowedHost) {
                throw IOException("URL catalogue non autorisée")
            }
            val connection = current.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (attempt == 5 || location.isNullOrBlank()) throw IOException("Redirection catalogue invalide")
                current = URL(current, location)
                return@repeat
            }
            if (code !in 200..299) {
                connection.disconnect()
                throw IOException("HTTP $code")
            }
            return connection
        }
        throw IOException("Trop de redirections catalogue")
    }

    private fun parse(json: JSONObject): OnlineBook? {
        val formats = json.optJSONObject("formats") ?: return null
        val epub = formats.optString("application/epub+zip").takeIf { it.isNotBlank() } ?: return null

        val authors = json.optJSONArray("authors")
        val author = if (authors != null && authors.length() > 0) {
            flipName(authors.getJSONObject(0).optString("name"))
        } else "Auteur inconnu"

        return OnlineBook(
            id = json.getLong("id"),
            title = json.optString("title").ifBlank { "Sans titre" },
            author = author.ifBlank { "Auteur inconnu" },
            language = json.optJSONArray("languages")?.optString(0).orEmpty(),
            epubUrl = epub,
            coverUrl = formats.optString("image/jpeg").takeIf { it.isNotBlank() }
        )
    }

    /** « Hugo, Victor » -> « Victor Hugo ». */
    private fun flipName(name: String): String {
        val parts = name.split(", ", limit = 2)
        return if (parts.size == 2) "${parts[1]} ${parts[0]}".trim() else name.trim()
    }
}
