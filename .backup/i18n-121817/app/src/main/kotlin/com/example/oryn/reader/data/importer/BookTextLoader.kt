package com.example.oryn.reader.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.graphics.Typeface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.LruCache
import android.util.Xml
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipInputStream
import kotlin.concurrent.withLock

sealed interface BookLoadResult {
    /** Livre texte : [pages] remplie. Livre PDF : [pdf] fourni, [pages] vide. */
    data class Success(
        val pages: List<String>,
        val pdf: PdfPages? = null,
        val rich: RichBook? = null,
        /** Page HTML d'origine, affichée telle quelle (mise en page respectée). */
        val html: String? = null
    ) : BookLoadResult
    data class Error(val message: String) : BookLoadResult
}

/**
 * PDF rendu page par page à la demande (au lieu de tout charger en mémoire).
 * Un petit cache garde les dernières pages. À fermer avec [close] en quittant le lecteur.
 */
class PdfPages internal constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** Couche texte (sélection, liens, sommaire) : ouverte seulement quand on en a besoin. */
    val text: PdfTextEngine
) : AutoCloseable {

    val pageCount: Int = renderer.pageCount

    private val lock = ReentrantLock()
    private val cache = LruCache<Int, Bitmap>(6)
    private var closed = false

    suspend fun render(index: Int, widthPx: Int = 1080): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed || index !in 0 until pageCount) return@withLock null
            cache.get(index)?.let { return@withLock it }
            runCatching {
                renderer.openPage(index).use { page ->
                    val height = (page.height.toFloat() * widthPx / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }.getOrNull()?.also { cache.put(index, it) }
        }
    }

    override fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            cache.evictAll()
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
            runCatching { text.close() }
        }
    }
}

class BookTextLoader(private val context: Context) {

    suspend fun loadBook(filePath: String, format: String): BookLoadResult = withContext(Dispatchers.IO) {
        if (filePath.isBlank()) return@withContext BookLoadResult.Error("Chemin de fichier invalide.")
        val uri = Uri.parse(filePath)

        runCatching {
            when (format.uppercase()) {
                "TXT" -> {
                    val text = loadTxt(uri)?.takeIf { it.isNotBlank() }
                        ?: return@runCatching BookLoadResult.Error("Le fichier texte est vide ou inaccessible.")
                    val clean = text.replace(Regex("\\n{3,}"), "\n\n").trim()
                    BookLoadResult.Success(emptyList(), null, RichBook(clean, emptyList(), autoLinks(clean, emptyList()), txtToc(clean)))
                }
                "EPUB" -> {
                    val rich = loadEpubRich(uri)
                    if (rich == null || rich.text.isBlank()) BookLoadResult.Error("Aucun contenu textuel lisible trouvé dans cet EPUB.")
                    else BookLoadResult.Success(emptyList(), null, rich)
                }
                "HTML" -> {
                    val html = loadHtml(uri)?.takeIf { it.isNotBlank() }
                        ?: return@runCatching BookLoadResult.Error("Le fichier HTML est vide ou inaccessible.")
                    BookLoadResult.Success(emptyList(), null, null, html)
                }
                "PDF" -> loadPdf(uri)
                else -> BookLoadResult.Error("Format non supporté pour la lecture directe.")
            }
        }.getOrElse {
            if (it is CancellationException) throw it
            BookLoadResult.Error("Erreur de chargement : ${it.localizedMessage}")
        }
    }

    // ── TXT : UTF-8 strict, repli windows-1252 pour les vieux fichiers ──
    private fun loadTxt(uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, charset("windows-1252"))
        }
        return text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
    }

    // ── HTML : lu tel quel (encodage déclaré dans la page, sinon UTF-8, sinon windows-1252) ──
    private fun loadHtml(uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val declared = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
            .find(head)?.groupValues?.get(1)
        val charset = declared?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() }
        if (charset != null) return String(bytes, charset)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, charset("windows-1252"))
        }
    }

    // ── PDF : ouverture seule, pages rendues à la demande ──
    private fun loadPdf(uri: Uri): BookLoadResult {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return BookLoadResult.Error("Impossible d'ouvrir le document PDF.")
        return try {
            val renderer = PdfRenderer(descriptor)
            if (renderer.pageCount == 0) {
                renderer.close()
                descriptor.close()
                BookLoadResult.Error("Ce PDF ne contient aucune page.")
            } else {
                BookLoadResult.Success(emptyList(), PdfPages(descriptor, renderer, PdfTextEngine(context.applicationContext, uri)))
            }
        } catch (e: Exception) {
            runCatching { descriptor.close() }
            throw e
        }
    }

    // ── EPUB : container.xml -> OPF -> spine (ordre réel de lecture), avec styles, liens et ancres ──
    private class RawLink(val start: Int, val end: Int, val href: String, val file: String)

    private fun loadEpubRich(uri: Uri): RichBook? {
        val files = LinkedHashMap<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!entry.isDirectory && (isHtml(name) || name.endsWith(".opf", true) ||
                            name.endsWith(".ncx", true) || name.equals("META-INF/container.xml", true))
                    ) {
                        files[name] = zip.readBytes()
                    }
                }
            }
        }

        val opfPath = files["META-INF/container.xml"]
            ?.let { runCatching { rootfilePath(it) }.getOrNull() }
            ?: files.keys.firstOrNull { it.endsWith(".opf", true) }
        val opfData = opfPath?.let { runCatching { parseOpf(files[it] ?: return@runCatching null) }.getOrNull() }
        val opfBase = opfPath?.substringBeforeLast('/', "") ?: ""

        val ordered = opfPath?.let { opf ->
            runCatching {
                val (manifest, spine) = parseOpf(files[opf] ?: return@runCatching null)
                val base = opf.substringBeforeLast('/', "")
                spine.mapNotNull { id ->
                    manifest[id]
                        ?.takeUnless { "nav" in it.properties.split(' ') }
                        ?.let { resolvePath(base, it.href) }
                }.filter { files.containsKey(it) }
            }.getOrNull()
        }?.takeIf { it.isNotEmpty() } ?: files.keys.filter { isHtml(it) }.sorted()

        val all = SpannableStringBuilder()
        val fileStart = HashMap<String, Int>()
        val anchors = HashMap<String, Int>()
        val rawLinks = ArrayList<RawLink>()

        for (name in ordered) {
            val bytes = files[name] ?: continue
            val chapter = htmlToSpanned(String(bytes, Charsets.UTF_8))
            val ids = extractMarkers(chapter)
            if (chapter.length < 20) continue
            if (all.isNotEmpty()) all.append("\n\n")
            val base = all.length
            fileStart[name] = base
            for ((id, pos) in ids) anchors.putIfAbsent("$name#$id", base + pos)
            for (u in chapter.getSpans(0, chapter.length, URLSpan::class.java)) {
                val s = chapter.getSpanStart(u)
                val e = chapter.getSpanEnd(u)
                if (s >= 0 && e > s && !u.url.isNullOrBlank()) rawLinks += RawLink(base + s, base + e, u.url, name)
            }
            all.append(chapter)
        }
        if (all.isEmpty()) return null

        val links = rawLinks.mapNotNull { raw ->
            val href = raw.href.trim()
            val scheme = href.substringBefore(':', "").lowercase()
            val target = when {
                scheme in setOf("http", "https", "mailto", "tel") -> LinkTarget.External(href)
                else -> {
                    val filePart = href.substringBefore('#')
                    val fragment = Uri.decode(href.substringAfter('#', ""))
                    val dir = raw.file.substringBeforeLast('/', "")
                    val path = if (filePart.isEmpty()) raw.file else resolvePath(dir, filePart)
                    val offset = (if (fragment.isNotEmpty()) anchors["$path#$fragment"] else null)
                        ?: fileStart[path]
                    offset?.let { LinkTarget.Internal(it) }
                }
            } ?: return@mapNotNull null
            LinkSpan(raw.start, raw.end, target)
        }.sortedBy { it.start }

        val text = all.toString()
        val spans = collectSpans(all)
        val toc = buildToc(opfData, opfBase, files, fileStart, anchors, text, spans)
        return RichBook(text, spans, links + autoLinks(text, links), toc)
    }

    /** HTML -> texte mis en forme. Les attributs id deviennent des marqueurs invisibles (voir [extractMarkers]). */
    private fun htmlToSpanned(html: String): SpannableStringBuilder {
        val cleaned = html
            .replace(Regex("(?is)<head.*?</head>"), "")
            .replace(Regex("(?is)<style.*?</style>"), "")
            .replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("(?is)<([a-z][a-z0-9]*)\\b([^>]*?)\\sid\\s*=\\s*([\"'])([^\"']+)\\3([^>]*)>")) {
                it.value + "\uE000" + it.groupValues[4] + "\uE001"
            }
            .replace(Regex("(?i)<li[^>]*>"), "<li>• ")
        val sb = SpannableStringBuilder(HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_LEGACY))

        // images vides
        while (true) {
            val i = sb.indexOf('\uFFFC')
            if (i < 0) break
            sb.delete(i, i + 1)
        }
        // espaces en fin de ligne, lignes vides en trop
        for (m in Regex("[ \\t]+\\n").findAll(sb.toString()).toList().asReversed()) {
            sb.delete(m.range.first, m.range.last)
        }
        for (m in Regex("\\n{3,}").findAll(sb.toString()).toList().asReversed()) {
            sb.delete(m.range.first + 2, m.range.last + 1)
        }
        while (sb.isNotEmpty() && sb[sb.length - 1].isWhitespace()) sb.delete(sb.length - 1, sb.length)
        return sb
    }

    /** Relève la position de chaque ancre (id) puis retire les marqueurs du texte. */
    private fun extractMarkers(sb: SpannableStringBuilder): List<Pair<String, Int>> {
        val found = Regex("\uE000([^\uE001]*)\uE001").findAll(sb.toString()).toList()
        val result = ArrayList<Pair<String, Int>>(found.size)
        var removed = 0
        for (m in found) {
            result += m.groupValues[1] to (m.range.first - removed)
            removed += m.value.length
        }
        for (m in found.asReversed()) sb.delete(m.range.first, m.range.last + 1)
        return result
    }

    private fun collectSpans(sb: Spanned): List<RichSpan> {
        val out = ArrayList<RichSpan>()
        for (span in sb.getSpans(0, sb.length, Any::class.java)) {
            val s = sb.getSpanStart(span)
            val e = sb.getSpanEnd(span)
            if (s < 0 || e <= s) continue
            when (span) {
                is StyleSpan -> {
                    if (span.style and Typeface.BOLD != 0) out += RichSpan(s, e, SpanKind.BOLD)
                    if (span.style and Typeface.ITALIC != 0) out += RichSpan(s, e, SpanKind.ITALIC)
                }
                is RelativeSizeSpan -> out += RichSpan(s, e, SpanKind.SIZE, span.sizeChange)
                is SuperscriptSpan -> out += RichSpan(s, e, SpanKind.SUPER)
                is SubscriptSpan -> out += RichSpan(s, e, SpanKind.SUB)
                is UnderlineSpan -> out += RichSpan(s, e, SpanKind.UNDERLINE)
                is StrikethroughSpan -> out += RichSpan(s, e, SpanKind.STRIKE)
                is TypefaceSpan -> if (span.family == "monospace") out += RichSpan(s, e, SpanKind.MONO)
            }
        }
        return out
    }

    private fun isHtml(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm")
    }

    private data class ManifestItem(val href: String, val properties: String, val mediaType: String = "")
    private data class OpfData(val manifest: Map<String, ManifestItem>, val spine: List<String>, val tocId: String?)

    private fun newParser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), null) }

    private fun rootfilePath(bytes: ByteArray): String? {
        val parser = newParser(bytes)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name.substringAfter(':') == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
            }
            parser.next()
        }
        return null
    }

    private fun parseOpf(bytes: ByteArray): OpfData {
        val manifest = LinkedHashMap<String, ManifestItem>()
        val spine = mutableListOf<String>()
        var tocId: String? = null
        val parser = newParser(bytes)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':')) {
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (id != null && href != null) {
                            manifest[id] = ManifestItem(
                                href,
                                parser.getAttributeValue(null, "properties") ?: "",
                                parser.getAttributeValue(null, "media-type") ?: ""
                            )
                        }
                    }
                    "spine" -> tocId = parser.getAttributeValue(null, "toc")
                    "itemref" -> {
                        val idref = parser.getAttributeValue(null, "idref")
                        if (idref != null && parser.getAttributeValue(null, "linear") != "no") spine.add(idref)
                    }
                }
            }
            parser.next()
        }
        return OpfData(manifest, spine, tocId)
    }

    // ── Sommaire : nav EPUB 3, sinon NCX (EPUB 2), sinon titres du texte ──
    private fun buildToc(
        opf: OpfData?,
        base: String,
        files: Map<String, ByteArray>,
        fileStart: Map<String, Int>,
        anchors: Map<String, Int>,
        text: String,
        spans: List<RichSpan>
    ): List<TocEntry> {
        val entries = ArrayList<TocEntry>()
        fun offsetOf(dir: String, href: String): Int? {
            val filePart = href.substringBefore('#')
            if (filePart.isEmpty()) return null
            val fragment = Uri.decode(href.substringAfter('#', ""))
            val path = resolvePath(dir, filePart)
            return (if (fragment.isNotEmpty()) anchors["$path#$fragment"] else null) ?: fileStart[path]
        }
        if (opf != null) {
            val nav = opf.manifest.values.firstOrNull { "nav" in it.properties.split(' ') }
            val ncx = opf.tocId?.let { opf.manifest[it] }
                ?: opf.manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
            if (nav != null) {
                val path = resolvePath(base, nav.href)
                val dir = path.substringBeforeLast('/', "")
                files[path]?.let { bytes ->
                    runCatching { parseNavToc(String(bytes, Charsets.UTF_8)) }.getOrDefault(emptyList()).forEach { (title, href, level) ->
                        offsetOf(dir, href)?.let { entries += TocEntry(title, it, level) }
                    }
                }
            }
            if (entries.size < 2 && ncx != null) {
                entries.clear()
                val path = resolvePath(base, ncx.href)
                val dir = path.substringBeforeLast('/', "")
                files[path]?.let { bytes ->
                    runCatching { parseNcxToc(bytes) }.getOrDefault(emptyList()).forEach { (title, href, level) ->
                        offsetOf(dir, href)?.let { entries += TocEntry(title, it, level) }
                    }
                }
            }
        }
        if (entries.size < 2) {
            entries.clear()
            entries += headingToc(text, spans)
        }
        return entries.distinctBy { it.offset to it.title }
    }

    private fun parseNavToc(html: String): List<Triple<String, String, Int>> {
        val body = Regex("(?is)<nav\\b[^>]*epub:type\\s*=\\s*[\"'][^\"']*\\btoc\\b[^\"']*[\"'][^>]*>(.*?)</nav>").find(html)?.groupValues?.get(1)
            ?: Regex("(?is)<nav\\b[^>]*>(.*?)</nav>").find(html)?.groupValues?.get(1)
            ?: return emptyList()
        val out = ArrayList<Triple<String, String, Int>>()
        var depth = 0
        val token = Regex("(?is)<(/?)(?:ol|ul)\\b[^>]*>|<a\\b[^>]*?href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>(.*?)</a>")
        for (m in token.findAll(body)) {
            if (m.groups[2] == null) {
                if (m.groupValues[1] == "/") depth-- else depth++
            } else {
                val title = HtmlCompat.fromHtml(m.groupValues[3], HtmlCompat.FROM_HTML_MODE_LEGACY)
                    .toString().replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
                if (title.isNotEmpty()) out += Triple(title, m.groupValues[2], (depth - 1).coerceAtLeast(0))
            }
        }
        return out
    }

    private fun parseNcxToc(bytes: ByteArray): List<Triple<String, String, Int>> {
        val out = ArrayList<Triple<String, String, Int>>()
        val parser = newParser(bytes)
        var depth = 0
        var inLabel = false
        val titles = ArrayList<String>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "navPoint" -> { depth++; titles.add("") }
                    "navLabel" -> inLabel = true
                    "text" -> if (inLabel && titles.isNotEmpty()) titles[titles.size - 1] = parser.nextText().trim()
                    "content" -> {
                        val src = parser.getAttributeValue(null, "src")
                        if (src != null && titles.isNotEmpty() && titles.last().isNotEmpty()) {
                            out += Triple(titles.last(), src, (depth - 1).coerceAtLeast(0))
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "navPoint" -> { depth--; if (titles.isNotEmpty()) titles.removeAt(titles.size - 1) }
                    "navLabel" -> inLabel = false
                }
            }
            parser.next()
        }
        return out
    }

    /** Sommaire de secours : les passages en gros caractères (titres) du livre. */
    private fun headingToc(text: String, spans: List<RichSpan>): List<TocEntry> {
        val out = ArrayList<TocEntry>()
        for (s in spans.filter { it.kind == SpanKind.SIZE && it.value >= 1.15f }.sortedBy { it.start }) {
            if (s.end - s.start > 120 || s.end > text.length) continue
            val title = text.substring(s.start, s.end).replace(Regex("\\s+"), " ").trim()
            if (title.length < 2) continue
            out += TocEntry(title, s.start, if (s.value >= 1.4f) 0 else 1)
        }
        return out
    }

    /** Résout un href relatif au dossier de l'OPF : décode %XX, retire #ancre, gère « .. ». */
    private fun resolvePath(base: String, href: String): String {
        val clean = Uri.decode(href.substringBefore('#'))
        val parts = ArrayDeque<String>()
        (if (base.isEmpty()) clean else "$base/$clean").split('/').forEach { seg ->
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(seg)
            }
        }
        return parts.joinToString("/")
    }

}
