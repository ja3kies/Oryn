package com.example.oryn.reader.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class BookMetadata(
    val title: String,
    val author: String,
    val format: String,
    /** Image de couverture trouvée dans l'EPUB (octets bruts), sinon null. */
    val coverBytes: ByteArray? = null
)

private class EpubInfo(val title: String?, val author: String?, val cover: ByteArray?, val description: String? = null)
private class OpfInfo(val title: String?, val author: String?, val coverPath: String?, val description: String? = null)
private class ManifestItem(val id: String, val href: String, val type: String, val props: String)

/** Lit titre / auteur / format / couverture d'un fichier (sélecteur Android ou fichier local). */
class BookMetadataReader(private val context: Context) {
    private companion object {
        const val MAX_XML_BYTES = 2 * 1024 * 1024
        const val MAX_COVER_BYTES = 15 * 1024 * 1024
        const val MAX_ZIP_ENTRIES = 20_000
    }

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw IOException("EPUB metadata exceeds size limit")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    /** Renvoie null si le format n'est pas pris en charge. */
    fun read(uri: Uri): BookMetadata? {
        val fileName = displayName(uri) ?: "Livre"
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val mime = context.contentResolver.getType(uri)

        val format = when {
            extension == "epub" || mime == "application/epub+zip" -> "EPUB"
            extension == "pdf" || mime == "application/pdf" -> "PDF"
            extension == "txt" || mime == "text/plain" -> "TXT"
            extension in setOf("html", "htm", "xhtml") || mime == "text/html" || mime == "application/xhtml+xml" -> "HTML"
            else -> return null
        }

        val fallbackTitle = fileName.substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Livre sans titre" }

        val epub = if (format == "EPUB") readEpub(uri) else null
        val html = if (format == "HTML") readHtmlHead(uri) else null

        return BookMetadata(
            title = epub?.title ?: html?.first ?: fallbackTitle,
            author = epub?.author ?: html?.second ?: "Auteur inconnu",
            format = format,
            coverBytes = epub?.cover
        )
    }

    /** Titre (balise title) et auteur (meta author) d'une page HTML, lus dans ses premiers Ko. */
    private fun readHtmlHead(uri: Uri): Pair<String?, String?>? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(65_536)
            val n = input.read(buffer)
            if (n > 0) buffer.copyOf(n) else null
        } ?: return@runCatching null
        val head = String(bytes, Charsets.UTF_8)
        val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(head)?.groupValues?.get(1)
            ?.let { androidx.core.text.HtmlCompat.fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString() }
            ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
        val author = Regex("(?is)<meta[^>]*name\\s*=\\s*[\"']author[\"'][^>]*content\\s*=\\s*[\"']([^\"']*)[\"']").find(head)
            ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        title to author
    }.getOrNull()

    /** Couverture seule, pour les livres déjà importés ou téléchargés. */
    fun extractEpubCover(uri: Uri): ByteArray? = readEpub(uri)?.cover

    /** Résumé de l'œuvre (balise dc:description de l'EPUB), nettoyé du HTML ; null s'il n'y en a pas. */
    fun readDescription(uri: Uri): String? = readEpub(uri)?.description

    private fun displayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }

    // ── EPUB : container.xml -> OPF (titre, auteur, manifeste) -> image de couverture ──
    private fun readEpub(uri: Uri): EpubInfo? = runCatching {
        val container = openZip(uri) { zip ->
            if (findEntry(zip, "META-INF/container.xml")) ByteArrayInputStream(zip.readBounded(MAX_XML_BYTES)) else null
        } ?: return@runCatching null
        val opfPath = rootfilePath(container) ?: return@runCatching null

        val opfStream = openZip(uri) { zip ->
            if (findEntry(zip, opfPath)) ByteArrayInputStream(zip.readBounded(MAX_XML_BYTES)) else null
        } ?: return@runCatching null
        val opf = parseOpf(opfStream, opfPath)

        val cover = opf.coverPath?.let { path ->
            openZip(uri) { zip -> if (findEntry(zip, path)) zip.readBounded(MAX_COVER_BYTES) else null }
        }
        EpubInfo(opf.title, opf.author, cover, opf.description)
    }.getOrNull()

    private fun <T> openZip(uri: Uri, block: (ZipInputStream) -> T?): T? =
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use(block)
        }

    private fun findEntry(zip: ZipInputStream, name: String): Boolean {
        var count = 0
        while (true) {
            val entry = zip.nextEntry ?: return false
            if (++count > MAX_ZIP_ENTRIES) throw IOException("EPUB contains too many entries")
            if (entry.name.equals(name, ignoreCase = true)) return true
        }
    }

    private fun rootfilePath(stream: InputStream): String? {
        val parser = Xml.newPullParser()
        parser.setInput(stream, null)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG &&
                parser.name.substringAfter(':') == "rootfile"
            ) {
                return parser.getAttributeValue(null, "full-path")
            }
            parser.next()
        }
        return null
    }

    private fun parseOpf(stream: InputStream, opfPath: String): OpfInfo {
        val parser = Xml.newPullParser()
        parser.setInput(stream, null)
        var title: String? = null
        var author: String? = null
        var coverId: String? = null
        var description: String? = null
        val items = mutableListOf<ManifestItem>()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':')) {
                    "title" -> if (title == null) {
                        title = parser.nextText().trim().takeIf { it.isNotBlank() }
                    }
                    "description" -> if (description == null) {
                        description = runCatching { parser.nextText() }.getOrNull()
                            ?.let { androidx.core.text.HtmlCompat.fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT).toString() }
                            ?.trim()?.takeIf { it.isNotBlank() }
                    }
                    "creator" -> if (author == null) {
                        author = parser.nextText().trim().takeIf { it.isNotBlank() }
                    }
                    // EPUB 2 : <meta name="cover" content="id-de-l-image"/>
                    "meta" -> if (parser.getAttributeValue(null, "name") == "cover") {
                        coverId = parser.getAttributeValue(null, "content")
                    }
                    "item" -> items += ManifestItem(
                        id = parser.getAttributeValue(null, "id").orEmpty(),
                        href = parser.getAttributeValue(null, "href").orEmpty(),
                        type = parser.getAttributeValue(null, "media-type").orEmpty(),
                        props = parser.getAttributeValue(null, "properties").orEmpty()
                    )
                }
            }
            parser.next()
        }

        val images = items.filter { it.type.startsWith("image/") && it.href.isNotBlank() }
        val coverItem = images.firstOrNull { "cover-image" in it.props.split(' ') } // EPUB 3
            ?: images.firstOrNull { coverId != null && it.id == coverId }
            ?: images.firstOrNull {
                it.id.contains("cover", ignoreCase = true) || it.href.contains("cover", ignoreCase = true)
            }

        val coverPath = coverItem?.let {
            resolveZipPath(opfPath.substringBeforeLast('/', ""), Uri.decode(it.href.substringBefore('#')))
        }
        return OpfInfo(title, author, coverPath, description)
    }

    /** Résout un chemin relatif (avec ./ et ../) à partir du dossier de l'OPF. */
    private fun resolveZipPath(baseDir: String, href: String): String {
        val parts = ArrayList<String>()
        val full = if (baseDir.isEmpty()) href else "$baseDir/$href"
        for (segment in full.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }
}
