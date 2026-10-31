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
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    // Domaines actuellement référencés par la FAQ officielle.
    private val MIRRORS = listOf(
        "https://annas-archive.gl",
        "https://annas-archive.pk",
        "https://annas-archive.gd"
    )

    // Autorisation exacte : pas de sous-domaines implicites ni de domaines ressemblants.
    private val ALLOWED_DOWNLOAD_HOSTS = setOf(
        "annas-archive.gl", "annas-archive.pk", "annas-archive.gd"
    )

    private fun isAllowedDownloadHost(url: String): Boolean {
        val parsed = try { java.net.URI(url) } catch (_: Exception) { return false }
        return parsed.scheme.equals("https", ignoreCase = true) &&
            parsed.host?.lowercase() in ALLOWED_DOWNLOAD_HOSTS
    }

    private const val MAX_REDIRECTS = 5

    private fun fetchHtml(url: String): String? {
        var current = url
        repeat(MAX_REDIRECTS + 1) { attempt ->
            if (!isAllowedDownloadHost(current)) return null
            val request = Request.Builder().url(current).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    if (attempt == MAX_REDIRECTS) return null
                    val location = response.header("Location") ?: return null
                    current = response.request.url.resolve(location)?.toString() ?: return null
                } else {
                    return if (response.isSuccessful) response.body?.string() else null
                }
            }
        }
        return null
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

            if (md5.matches(Regex("^[a-fA-F0-9]{32}$"))) {
                books.add(AnnasBook(md5, title, author, format.uppercase(), coverUrl))
            }
        }
        return books
    }

    suspend fun getDownloadLinks(md5: String): List<String> = withContext(Dispatchers.IO) {
        if (!md5.matches(Regex("^[a-fA-F0-9]{32}$"))) return@withContext emptyList()
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
        val parent = destination.parentFile ?: return@withContext false
        val temp = File(parent, destination.name + ".part")
        val staged = File(parent, destination.name + ".publish")
        val backup = File(parent, destination.name + ".backup")
        try {
            if (!isAllowedDownloadHost(url)) return@withContext false
            parent.mkdirs()
            var current = url
            var downloaded = false
            repeat(MAX_REDIRECTS + 1) { attempt ->
                if (downloaded) return@repeat
                if (!isAllowedDownloadHost(current)) return@withContext false
                val request = Request.Builder().url(current).header("User-Agent", USER_AGENT).build()
                client.newCall(request).execute().use { response ->
                    if (response.isRedirect) {
                        if (attempt == MAX_REDIRECTS) return@withContext false
                        val location = response.header("Location") ?: return@withContext false
                        current = response.request.url.resolve(location)?.toString() ?: return@withContext false
                    } else {
                        if (!response.isSuccessful) return@withContext false
                        val body = response.body ?: return@withContext false
                        if (body.contentLength() > MAX_DOWNLOAD_BYTES) return@withContext false
                        var written = 0L
                        temp.outputStream().use { out ->
                            body.byteStream().use { input ->
                                val buf = ByteArray(8192)
                                while (true) {
                                    val count = input.read(buf)
                                    if (count < 0) break
                                    written += count
                                    if (written > MAX_DOWNLOAD_BYTES) {
                                        throw java.io.IOException("Download exceeds size limit")
                                    }
                                    out.write(buf, 0, count)
                                }
                            }
                        }
                        if (written < 1024) return@withContext false
                        downloaded = true
                    }
                }
            }
            if (!downloaded) return@withContext false

            // Vérification du format avant de publier le fichier dans le cache.
            val head = ByteArray(8)
            temp.inputStream().use { it.read(head) }
            val isEpub = head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
            val isPdf = head[0] == 0x25.toByte() && head[1] == 0x50.toByte() &&
                head[2] == 0x44.toByte() && head[3] == 0x46.toByte()
            if (!isEpub && !isPdf) return@withContext false

            temp.copyTo(staged, overwrite = true)
            if (backup.exists() && !backup.delete()) return@withContext false
            val hadDestination = destination.exists()
            if (hadDestination && !destination.renameTo(backup)) return@withContext false
            if (!staged.renameTo(destination)) {
                if (hadDestination) backup.renameTo(destination)
                return@withContext false
            }
            if (backup.exists()) backup.delete()
            true
        } catch (_: Exception) {
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            false
        }
        finally {
            temp.delete()
            staged.delete()
        }
    }
}
