package com.example.oryn.reader.data.importer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Mode « texte » d'un PDF : le texte de toutes les pages est remis en forme (titres, paragraphes, listes),
 * sans en-têtes ni numéros de page, puis lu comme un livre numérique (pages, sélection, annotations, recherche).
 *
 * [pageOffsets] : position (en caractères) du début de chaque page du PDF dans [rich].text.
 * [scanned] : le PDF ne contient presque pas de texte (pages scannées) ; le mode texte n'a alors pas de sens.
 */
class PdfReflowBook(val rich: RichBook, val pageOffsets: IntArray, val scanned: Boolean) {
    /** Page du PDF (0..) qui contient la position [offset] du texte. */
    fun pageAt(offset: Int): Int {
        var lo = 0
        var hi = pageOffsets.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (pageOffsets[mid] <= offset) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }

    /** Position (fraction du texte) du début de la page [page]. */
    fun fractionOfPage(page: Int): Float {
        val len = max(1, rich.text.length)
        return (pageOffsets.getOrElse(page.coerceIn(0, max(0, pageOffsets.size - 1))) { 0 }.toFloat() / len).coerceIn(0f, 1f)
    }
}

/** Paragraphe ou titre reconnu sur une page. [heading] : -1 = texte, 0..2 = niveau de titre. */
internal class ReflowBlock(
    val heading: Int,
    val text: String,
    val spans: List<RichSpan>,
    val list: Boolean
)

/** Un mot en points PDF (pas en fractions de page), avec sa taille de police. */
private class W(
    val text: String,
    val l: Float, val t: Float, val r: Float, val b: Float,
    val size: Float, val bold: Boolean, val italic: Boolean
) {
    val cx: Float get() = (l + r) / 2f
    val cy: Float get() = (t + b) / 2f
}

private class RLine(val words: List<W>) {
    val l = words.minOf { it.l }
    val r = words.maxOf { it.r }
    val t = words.minOf { it.t }
    val b = words.maxOf { it.b }
    val cy = words.map { it.cy }.average().toFloat()
    val size = words.maxOf { it.size }
    val text: String = words.joinToString(" ") { it.text }
    val chars: Int = words.sumOf { it.text.length }
    val boldRatio: Float = words.count { it.bold }.toFloat() / words.size
}

internal object PdfReflow {
    private const val DEFAULT_W = 595f
    private const val DEFAULT_H = 842f
    private val LIST_MARK = Regex("^(?:[•·▪◦‣●○■□]|[-–—]|\\d{1,3}[.)]|\\(\\d{1,3}\\)|[a-zA-Z][.)])\\s+")
    private val PAGE_NUMBER = Regex("^(?:page\\s*|p\\.\\s*)?[0-9ivxlcdmIVXLCDM]{1,6}(?:\\s*(?:/|sur|of)\\s*\\d{1,5})?$", RegexOption.IGNORE_CASE)

    // ───────────────────────── statistiques du document ─────────────────────────

    /** Taille de police du corps du texte (la plus fréquente, pondérée par les caractères) + part de gras / d'italique. */
    class DocStats(val body: Float, val ignoreBold: Boolean, val ignoreItalic: Boolean)

    fun docStats(samples: List<PdfTextPage>): DocStats {
        val hist = HashMap<Int, Int>()
        for (p in samples) for (w in p.words) {
            val s = sizeOf(w)
            hist[(s * 2f).toInt()] = (hist[(s * 2f).toInt()] ?: 0) + w.text.length
        }
        val modeKey = hist.maxByOrNull { it.value }?.key
        val body = if (modeKey == null) 10f else modeKey / 2f + 0.25f
        var total = 0
        var bold = 0
        var italic = 0
        for (p in samples) for (w in p.words) {
            if (abs(sizeOf(w) - body) <= 0.8f) {
                total += w.text.length
                if (w.bold) bold += w.text.length
                if (w.italic) italic += w.text.length
            }
        }
        val t = max(1, total).toFloat()
        return DocStats(max(5f, body), bold / t > 0.55f, italic / t > 0.55f)
    }

    private fun sizeOf(w: PdfWord): Float =
        if (w.size > 0f) w.size else max(4f, (w.b - w.t) * DEFAULT_H / 1.22f)

    // ───────────────────────── en-têtes et pieds de page ─────────────────────────

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("[0-9]+"), "#").replace(Regex("\\s+"), " ").trim()

    private fun toWords(page: PdfTextPage): List<W> {
        val pw = if (page.width > 0f) page.width else DEFAULT_W
        val ph = if (page.height > 0f) page.height else DEFAULT_H
        return page.words.mapNotNull { w ->
            val text = clean(w.text)
            if (text.isBlank()) null
            else {
                val size = if (w.size > 0f) w.size else max(4f, (w.b - w.t) * ph / 1.22f)
                W(text, w.l * pw, w.t * ph, w.r * pw, w.b * ph, size, w.bold, w.italic)
            }
        }
    }

    private fun clean(s: String): String = s
        .replace("­", "")
        .replace("ﬁ", "fi").replace("ﬂ", "fl").replace("ﬀ", "ff").replace("ﬃ", "ffi").replace("ﬄ", "ffl")
        .replace(" ", " ")

    private fun pageHeight(page: PdfTextPage): Float = if (page.height > 0f) page.height else DEFAULT_H

    /** Lignes du haut et du bas de la page, normalisées (chiffres → #) : sert à repérer ce qui se répète d'une page à l'autre. */
    fun edgeKeys(page: PdfTextPage): List<String> {
        val ph = pageHeight(page)
        val out = ArrayList<String>()
        for (line in rawLines(toWords(page))) {
            val top = line.cy < ph * 0.09f
            val bottom = line.cy > ph * 0.91f
            if (top || bottom) out += (if (top) "T:" else "B:") + norm(line.text)
        }
        return out
    }

    private fun isPageNumber(text: String): Boolean {
        val t = text.trim()
        return t.length <= 14 && t.any { it.isDigit() || it in "ivxlcdmIVXLCDM" } && PAGE_NUMBER.matches(t)
    }

    // ───────────────────────── analyse d'une page ─────────────────────────

    fun analyze(page: PdfTextPage, stats: DocStats, repeated: Set<String>): List<ReflowBlock> {
        var words = toWords(page)
        if (words.isEmpty()) return emptyList()
        val ph = pageHeight(page)

        // 1. retire en-têtes, pieds de page et numéros de page
        val drop = HashSet<W>()
        for (line in rawLines(words)) {
            val top = line.cy < ph * 0.09f
            val bottom = line.cy > ph * 0.91f
            if (!top && !bottom) continue
            val key = (if (top) "T:" else "B:") + norm(line.text)
            if (key in repeated || isPageNumber(line.text)) drop.addAll(line.words)
        }
        if (drop.isNotEmpty()) words = words.filter { it !in drop }
        if (words.isEmpty()) return emptyList()

        // 2. découpe en régions (colonnes, bandeaux) dans l'ordre de lecture
        val m = stats.body
        val regions = ArrayList<List<W>>()
        cut(words, m, 0, regions)

        // 3. écart « normal » entre deux lignes, mesuré sur toute la page
        val perRegionLines = regions.map { rawLines(it) }
        val gaps = ArrayList<Float>()
        for (lines in perRegionLines) for (i in 1 until lines.size) {
            val d = lines[i].cy - lines[i - 1].cy
            if (d > 0.6f * m && d < 2.2f * m) gaps += d
        }
        val pitch = if (gaps.isEmpty()) 1.25f * m else gaps.sorted()[gaps.size / 2]

        // 4. lignes → paragraphes
        val blocks = ArrayList<ReflowBlock>()
        for (lines in perRegionLines) blocks += assemble(lines, stats, pitch)
        return blocks
    }

    /** Regroupe des mots en lignes (même hauteur), de gauche à droite. */
    private fun rawLines(words: List<W>): List<RLine> {
        if (words.isEmpty()) return emptyList()
        val sorted = words.sortedWith(compareBy({ it.cy }, { it.l }))
        val groups = ArrayList<MutableList<W>>()
        var curCy = 0f
        var curH = 0f
        for (w in sorted) {
            val h = max(1f, w.b - w.t)
            if (groups.isNotEmpty() && abs(w.cy - curCy) <= 0.55f * min(curH, h)) {
                val g = groups.last()
                curCy = (curCy * g.size + w.cy) / (g.size + 1)
                curH = max(curH, h)
                g.add(w)
            } else {
                groups.add(mutableListOf(w))
                curCy = w.cy
                curH = h
            }
        }
        return groups.map { g -> RLine(g.sortedBy { it.l }) }
    }

    // ───────────── découpage en colonnes / bandeaux (XY-cut avec seuils relatifs à la police) ─────────────

    private fun cut(words: List<W>, m: Float, depth: Int, out: MutableList<List<W>>) {
        if (words.size < 12 || depth > 6) { out.add(words); return }
        val gx = verticalGutter(words, m)
        if (gx != null) {
            val left = words.filter { it.cx < gx }
            val right = words.filter { it.cx >= gx }
            if (left.size >= 5 && right.size >= 5) {
                cut(left, m, depth + 1, out)
                cut(right, m, depth + 1, out)
                return
            }
        }
        val gy = horizontalBreak(words, m)
        if (gy != null) {
            val top = words.filter { it.cy < gy }
            val bottom = words.filter { it.cy >= gy }
            if (top.isNotEmpty() && bottom.isNotEmpty()) {
                cut(top, m, depth + 1, out)
                cut(bottom, m, depth + 1, out)
                return
            }
        }
        out.add(words)
    }

    /** Milieu d'une bande verticale vide sur toute la hauteur de la région (gouttière entre deux colonnes). */
    private fun verticalGutter(words: List<W>, m: Float): Float? {
        if (rowCount(words, m) < 6) return null
        val sorted = words.sortedBy { it.l }
        val minX = sorted.first().l
        val maxX = words.maxOf { it.r }
        val width = maxX - minX
        if (width < 12f * m) return null
        var curEnd = sorted.first().r
        var best: Float? = null
        var bestWidth = 0f
        for (i in 1 until sorted.size) {
            val w = sorted[i]
            if (w.l > curEnd) {
                val gap = w.l - curEnd
                val mid = curEnd + gap / 2f
                if (gap >= 1.0f * m && gap > bestWidth && mid - minX > 0.12f * width && maxX - mid > 0.12f * width) {
                    bestWidth = gap
                    best = mid
                }
            }
            curEnd = max(curEnd, w.r)
        }
        return best
    }

    /** Grand blanc horizontal (changement de section, titre pleine largeur au-dessus de colonnes). */
    private fun horizontalBreak(words: List<W>, m: Float): Float? {
        val sorted = words.sortedBy { it.t }
        var curEnd = sorted.first().b
        var best: Float? = null
        var bestGap = 0f
        for (i in 1 until sorted.size) {
            val w = sorted[i]
            if (w.t > curEnd) {
                val gap = w.t - curEnd
                if (gap >= 2.2f * m && gap > bestGap) {
                    bestGap = gap
                    best = curEnd + gap / 2f
                }
            }
            curEnd = max(curEnd, w.b)
        }
        return best
    }

    private fun rowCount(words: List<W>, m: Float): Int {
        val sorted = words.sortedBy { it.cy }
        var rows = 1
        var last = sorted.first().cy
        for (w in sorted) {
            if (w.cy - last > 0.8f * m) { rows++; last = w.cy }
        }
        return rows
    }

    // ───────────────────────── lignes → paragraphes ─────────────────────────

    private class Para(val heading: Int, val firstLine: RLine) {
        val sb = StringBuilder()
        val spans = ArrayList<RichSpan>()
        var lines = 0
        var list = false
        var last: RLine = firstLine
        var boldStart = -1
        var boldEnd = -1
        var italicStart = -1
        var italicEnd = -1
        var size = firstLine.size
        var allBold = true
    }

    private fun assemble(lines: List<RLine>, stats: DocStats, pitch: Float): List<ReflowBlock> {
        if (lines.isEmpty()) return emptyList()
        val B = stats.body
        val lefts = lines.map { it.l }.sorted()
        val rights = lines.map { it.r }.sorted()
        val leftEdge = lefts[(lefts.size * 0.25f).toInt().coerceIn(0, lefts.size - 1)]
        val rightEdge = rights[(rights.size * 0.8f).toInt().coerceIn(0, rights.size - 1)]

        val paras = ArrayList<Para>()
        var cur: Para? = null
        var prev: RLine? = null
        for (line in lines) {
            val level = headingLevel(line, B)
            val p = prev
            val c = cur
            val start = when {
                c == null || p == null -> true
                level != c.heading -> true
                level >= 0 -> {
                    // titre sur plusieurs lignes : même taille et lignes serrées
                    abs(line.size - c.size) > 0.08f * c.size || (line.cy - p.cy) > 1.6f * pitch
                }
                line.cy - p.cy > 1.5f * pitch -> true
                LIST_MARK.containsMatchIn(line.text) -> true
                line.l - leftEdge > 0.9f * B && line.l - leftEdge < 8f * B && p.l - leftEdge < 0.9f * B -> true
                p.r < rightEdge - 3f * B && endsSentence(p.text) -> true
                else -> false
            }
            if (start) {
                cur?.let { paras += it }
                cur = Para(level, line).also { it.list = level < 0 && LIST_MARK.containsMatchIn(line.text) }
                addLine(cur, line, null, rightEdge, stats)
            } else {
                addLine(cur!!, line, prev, rightEdge, stats)
            }
            prev = line
        }
        cur?.let { paras += it }
        return paras.mapNotNull { finish(it, stats) }
    }

    private fun headingLevel(line: RLine, B: Float): Int {
        if (line.chars > 160) return -1
        return when {
            line.size >= B * 1.6f -> 0
            line.size >= B * 1.3f -> 1
            line.size >= B * 1.18f -> 2
            else -> -1
        }
    }

    private fun endsSentence(t: String): Boolean {
        val s = t.trimEnd()
        if (s.isEmpty()) return true
        return s.last() in ".!?…:;»”\"')]"
    }

    private fun addLine(p: Para, line: RLine, prev: RLine?, rightEdge: Float, stats: DocStats) {
        p.lines++
        p.last = line
        p.size = max(p.size, line.size)
        var sep = ""
        if (prev != null) {
            val tail = p.sb.toString()
            val firstWord = line.words.first().text
            sep = when {
                // coupure de mot en fin de ligne : « informa- tion » → « information »
                tail.length >= 2 && tail.last() in "-‐‑" && tail[tail.length - 2].isLetter() &&
                    firstWord.first().isLowerCase() -> {
                    p.sb.setLength(p.sb.length - 1)
                    trimSpans(p)
                    ""
                }
                // vers très courts : on garde le retour à la ligne
                prev.r < rightEdge - 8f * stats.body && p.heading < 0 -> "\n"
                else -> " "
            }
        }
        var first = true
        for (w in line.words) {
            val s = if (first) sep else " "
            first = false
            p.sb.append(s)
            val st = p.sb.length
            p.sb.append(w.text)
            val en = p.sb.length
            val bold = w.bold && !stats.ignoreBold
            val italic = w.italic && !stats.ignoreItalic
            if (!bold) p.allBold = false
            if (bold) {
                if (p.boldStart >= 0 && p.boldEnd == st - s.length && s == " ") p.boldEnd = en
                else { flushBold(p); p.boldStart = st; p.boldEnd = en }
            }
            if (italic) {
                if (p.italicStart >= 0 && p.italicEnd == st - s.length && s == " ") p.italicEnd = en
                else { flushItalic(p); p.italicStart = st; p.italicEnd = en }
            }
        }
    }

    private fun flushBold(p: Para) {
        if (p.boldStart >= 0 && p.boldEnd > p.boldStart) p.spans += RichSpan(p.boldStart, p.boldEnd, SpanKind.BOLD)
        p.boldStart = -1; p.boldEnd = -1
    }

    private fun flushItalic(p: Para) {
        if (p.italicStart >= 0 && p.italicEnd > p.italicStart) p.spans += RichSpan(p.italicStart, p.italicEnd, SpanKind.ITALIC)
        p.italicStart = -1; p.italicEnd = -1
    }

    /** Après avoir retiré le trait d'union final : ramène les styles à la nouvelle longueur. */
    private fun trimSpans(p: Para) {
        val len = p.sb.length
        if (p.boldEnd > len) p.boldEnd = len
        if (p.italicEnd > len) p.italicEnd = len
    }

    private fun finish(p: Para, stats: DocStats): ReflowBlock? {
        flushBold(p)
        flushItalic(p)
        val text = p.sb.toString().trim()
        if (text.isEmpty()) return null
        val lead = p.sb.length - p.sb.toString().trimStart().length
        var level = p.heading
        var spans: List<RichSpan> = p.spans.mapNotNull {
            val a = max(0, it.start - lead)
            val e = min(text.length, it.end - lead)
            if (e > a) RichSpan(a, e, it.kind, it.value) else null
        }
        // titre en gras seul (même taille que le texte) : une ou deux lignes courtes, sans point final
        if (level < 0 && !p.list && !stats.ignoreBold && p.allBold && p.lines <= 2 && text.length <= 110 &&
            text.last() !in ".;,"
        ) level = 2
        if (level >= 0) {
            val scale = (p.size / stats.body).coerceIn(1.0f, 1.35f)
            val list = ArrayList<RichSpan>()
            list += RichSpan(0, text.length, SpanKind.BOLD)
            if (scale >= 1.1f) list += RichSpan(0, text.length, SpanKind.SIZE, scale)
            spans = list
        }
        return ReflowBlock(level, text, spans, p.list)
    }
}

/** Assemble toutes les pages en un seul texte (avec sommaire) ; garde le résultat sur le disque. */
internal object PdfReflowBuilder {
    private const val VERSION = 3
    private const val WINDOW = 3

    private fun cacheFile(context: Context, uri: Uri, pageCount: Int): File {
        val dir = File(context.cacheDir, "oryn-reflow").apply { mkdirs() }
        return File(dir, "${uri.toString().hashCode()}-$pageCount-v$VERSION.bin")
    }

    suspend fun load(context: Context, uri: Uri, pageCount: Int): PdfReflowBook? = withContext(Dispatchers.IO) {
        val f = cacheFile(context, uri, pageCount)
        if (!f.exists()) return@withContext null
        runCatching {
            DataInputStream(f.inputStream().buffered()).use { inp ->
                if (inp.readInt() != VERSION) return@use null
                val scanned = inp.readBoolean()
                val textBytes = ByteArray(inp.readInt())
                inp.readFully(textBytes)
                val text = String(textBytes, Charsets.UTF_8)
                val offsets = IntArray(inp.readInt()) { inp.readInt() }
                val spans = ArrayList<RichSpan>()
                repeat(inp.readInt()) {
                    spans += RichSpan(inp.readInt(), inp.readInt(), SpanKind.entries[inp.readInt()], inp.readFloat())
                }
                val toc = ArrayList<TocEntry>()
                repeat(inp.readInt()) { toc += TocEntry(inp.readUTF(), inp.readInt(), inp.readInt()) }
                PdfReflowBook(RichBook(text, spans, autoLinks(text, emptyList()), toc), offsets, scanned)
            }
        }.getOrNull()
    }

    private fun save(context: Context, uri: Uri, pageCount: Int, book: PdfReflowBook) {
        runCatching {
            val f = cacheFile(context, uri, pageCount)
            DataOutputStream(f.outputStream().buffered()).use { out ->
                out.writeInt(VERSION)
                out.writeBoolean(book.scanned)
                val bytes = book.rich.text.toByteArray(Charsets.UTF_8)
                out.writeInt(bytes.size)
                out.write(bytes)
                out.writeInt(book.pageOffsets.size)
                book.pageOffsets.forEach { out.writeInt(it) }
                out.writeInt(book.rich.spans.size)
                for (s in book.rich.spans) {
                    out.writeInt(s.start); out.writeInt(s.end); out.writeInt(s.kind.ordinal); out.writeFloat(s.value)
                }
                out.writeInt(book.rich.toc.size)
                for (e in book.rich.toc) {
                    out.writeUTF(e.title.take(500)); out.writeInt(e.offset); out.writeInt(e.level)
                }
            }
        }
    }

    suspend fun build(
        context: Context,
        uri: Uri,
        engine: PdfTextEngine,
        pageCount: Int,
        onProgress: (Float) -> Unit
    ): PdfReflowBook {
        load(context, uri, pageCount)?.let { return it }
        return withContext(Dispatchers.Default) { assembleBook(context, uri, engine, pageCount, onProgress) }
    }

    private suspend fun assembleBook(
        context: Context,
        uri: Uri,
        engine: PdfTextEngine,
        pageCount: Int,
        onProgress: (Float) -> Unit
    ): PdfReflowBook {
        val ctx = currentCoroutineContext()

        // Statistiques du document : quelques pages réparties sur tout le livre
        val sampleIdx = if (pageCount <= 12) (0 until pageCount).toList()
        else (0 until 12).map { (it * (pageCount - 1)) / 11 }.distinct()
        val samples = sampleIdx.map { engine.page(it) }
        val stats = PdfReflow.docStats(samples)

        val edge = HashMap<Int, List<String>>()
        suspend fun edgeOf(i: Int): List<String> {
            if (i < 0 || i >= pageCount) return emptyList()
            return edge.getOrPut(i) { PdfReflow.edgeKeys(engine.page(i)) }
        }

        val text = StringBuilder()
        val spans = ArrayList<RichSpan>()
        val toc = ArrayList<TocEntry>()
        val offsets = IntArray(pageCount)
        var prevOpen = false
        var prevHyphen = false

        for (i in 0 until pageCount) {
            ctx.ensureActive()
            // En-têtes / pieds de page : lignes de bord qui reviennent sur plusieurs pages voisines
            val counts = HashMap<String, Int>()
            for (j in (i - WINDOW)..(i + WINDOW)) for (k in edgeOf(j).toSet()) counts[k] = (counts[k] ?: 0) + 1
            val repeated = counts.filterValues { it >= 2 }.keys
            edge.remove(i - WINDOW - 1)

            val blocks = PdfReflow.analyze(engine.page(i), stats, repeated)
            var first = true
            for (b in blocks) {
                val base: Int
                val merge = first && prevOpen && b.heading < 0 && !b.list &&
                    (prevHyphen || b.text.first().isLowerCase())
                if (text.isEmpty()) {
                    base = 0
                } else if (merge) {
                    if (prevHyphen) text.setLength(text.length - 1) else text.append(' ')
                    base = text.length
                } else {
                    text.append("\n\n")
                    base = text.length
                }
                if (first) offsets[i] = base
                first = false
                text.append(b.text)
                for (s in b.spans) spans += RichSpan(s.start + base, s.end + base, s.kind, s.value)
                if (b.heading in 0..1 && toc.size < 300) toc += TocEntry(b.text.replace('\n', ' ').take(120), base, b.heading)
                val t = b.text.trimEnd()
                prevOpen = b.heading < 0 && t.isNotEmpty() && t.last() !in ".!?…»”\"')]:;"
                prevHyphen = prevOpen && t.length >= 2 && t.last() in "-‐‑" && t[t.length - 2].isLetter()
                if (b.heading >= 0) { prevOpen = false; prevHyphen = false }
            }
            if (first) offsets[i] = text.length
            onProgress((i + 1f) / pageCount)
        }

        // Sommaire : signets du PDF s'il y en a, sinon les titres repérés
        val outline = engine.outline()
        val finalToc: List<TocEntry> =
            if (outline.size >= 2) outline.map { e ->
                TocEntry(e.title, offsets[(e.offset / PDF_STRIDE).coerceIn(0, pageCount - 1)], e.level)
            }.sortedBy { it.offset }
            else toc.fold(ArrayList<TocEntry>()) { acc, e ->
                if (acc.isEmpty() || acc.last().title != e.title) acc.add(e)
                acc
            }

        val scanned = text.length < max(200, pageCount * 60)
        val result = PdfReflowBook(
            RichBook(text.toString(), spans, autoLinks(text.toString(), emptyList()), finalToc),
            offsets, scanned
        )
        withContext(Dispatchers.IO) { save(context, uri, pageCount, result) }
        return result
    }
}
