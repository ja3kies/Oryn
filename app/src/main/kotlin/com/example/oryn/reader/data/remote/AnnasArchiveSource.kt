package com.example.oryn.reader.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.util.concurrent.TimeUnit

object AnnasArchiveSource {
    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(DdosGuardInterceptor(bootstrapClient))
        .build()

    private val MIRRORS = listOf(
        "https://annas-archive.gl",
        "https://annas-archive.se",
        "https://annas-archive.org"
    )

    /** Hosts autorisés pour les liens de download. Bloque les href externes arbitraires. */
    private val ALLOWED_DOWNLOAD_HOSTS = setOf(
        "annas-archive.gl", "annas-archive.se", "annas-archive.org",
        "libgen.li", "libgen.is", "libgen.rs", "libgen.st",
    )

    private fun isAllowedDownloadHost(url: String): Boolean {
        val host = try { java.net.URI(url).host?.lowercase() } catch (_: Exception) { null }
            ?: return false
        return ALLOWED_DOWNLOAD_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private const val MAX_DOWNLOAD_BYTES = 200L * 1024 * 1024
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun search(query: String, format: String = "epub"): List<AnnasBook> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        for (mirror in MIRRORS) {
            try {
                val url = "$mirror/search?index=&q=$encodedQuery&ext=$format&sort="
                // OkHttp + DdosGuardInterceptor (cookie bypass). Pas de WebView :
                // WebViewScraper requiert SYSTEM_ALERT_WINDOW, absent du manifest.
                val html = fetchHtml(url) ?: continue
                val result = parseSearchResults(html, format)
                if (result.isNotEmpty()) return result
            } catch (_: Exception) { }
        }
        return emptyList()
    }

    private fun fetchHtml(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private fun parseSearchResults(html: String, format: String): List<AnnasBook> {
        val books = mutableListOf<AnnasBook>()
        val doc: Document = Jsoup.parse(html)

        // Sélecteurs avec guillemets simples CSS (pas d'échappement Kotlin nécessaire)
        val items = doc.select("div[class*='justify-center']")
        for (item in items) {
            val rawHtml = item.html()
            val cleanHtml = if (rawHtml.trim().startsWith("<!--") && rawHtml.trim().endsWith("-->")) {
                rawHtml.trim().removePrefix("<!--").removeSuffix("-->")
            } else {
                val parentHtml = item.parent()?.html() ?: ""
                if (parentHtml.trim().startsWith("<!--") && parentHtml.trim().endsWith("-->")) {
                    parentHtml.trim().removePrefix("<!--").removeSuffix("-->")
                } else {
                    rawHtml
                }
            }
            val cleanDoc = Jsoup.parse(cleanHtml)

            val title = cleanDoc.selectFirst("h3")?.text() ?: continue
            val author = cleanDoc.selectFirst("div[class*='italic']")?.text() ?: "Auteur inconnu"
            val href = cleanDoc.selectFirst("a[href^='/md5/']")?.attr("href") ?: continue
            val md5 = href.substringAfterLast("/md5/").substringBefore("?").substringBefore("#")
            val coverUrl = cleanDoc.selectFirst("img")?.attr("src")

            if (md5.isNotBlank()) {
                books.add(AnnasBook(md5, title, author, format.uppercase(), coverUrl))
            }
        }
        return books
    }

    suspend fun getDownloadLinks(md5: String): List<String> = withContext(Dispatchers.IO) {
        val links = mutableListOf<String>()
        for (mirror in MIRRORS) {
            try {
                val md5Html = fetchHtml("$mirror/md5/$md5") ?: continue
                val adsRegex = Regex("href=[\"']([^\"']*ads\\.php\\?md5=[a-fA-F0-9]{32}[^\"']*)[\"']")
                for (m in adsRegex.findAll(md5Html)) {
                    val adsUrl = if (m.groupValues[1].startsWith("http")) m.groupValues[1] else mirror + m.groupValues[1]
                    try {
                        val adsHtml = fetchHtml(adsUrl) ?: continue
                        val getRegex = Regex("href=[\"']([^\"']*get\\.php\\?md5=[a-fA-F0-9]{32}[^\"']*)[\"']")
                        for (g in getRegex.findAll(adsHtml)) {
                            val getUrl = if (g.groupValues[1].startsWith("http")) g.groupValues[1] else "https://libgen.li/" + g.groupValues[1].removePrefix("/")
                            if (isAllowedDownloadHost(getUrl)) links.add(getUrl)
                        }
                    } catch (_: Exception) { }
                }
                val slowRegex = Regex("href=[\"']([^\"']*slow_download/[a-fA-F0-9]{32}[^\"']*)[\"']")
                for (m in slowRegex.findAll(md5Html)) {
                    val slowUrl = if (m.groupValues[1].startsWith("http")) m.groupValues[1] else mirror + m.groupValues[1]
                    if (isAllowedDownloadHost(slowUrl)) links.add(slowUrl)
                }
            } catch (_: Exception) { }
        }
        links.distinct()
    }

    suspend fun downloadFile(url: String, destination: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val scheme = android.net.Uri.parse(url).scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return@withContext false
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                if (body.contentLength() > MAX_DOWNLOAD_BYTES) return@withContext false
                var written = 0L
                destination.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(8192)
                        var n: Int
                        while (input.read(buf).also { n = it } > 0) {
                            written += n
                            if (written > MAX_DOWNLOAD_BYTES) return@withContext false
                            out.write(buf, 0, n)
                        }
                    }
                }
            }
            // Magic bytes check (EPUB = PK, PDF = %PDF, TXT = ASCII/UTF-8 probable)
            val head = ByteArray(8)
            destination.inputStream().use { it.read(head) }
            val isEpub = head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
            val isPdf = head[0] == 0x25.toByte() && head[1] == 0x50.toByte() &&
                        head[2] == 0x44.toByte() && head[3] == 0x46.toByte()
            if (!isEpub && !isPdf) {
                destination.delete()
                return@withContext false
            }
            true
        } catch (_: Exception) { false }
    }
}
