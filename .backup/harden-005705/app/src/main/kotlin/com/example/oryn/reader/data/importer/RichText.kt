package com.example.oryn.reader.data.importer

/** Mise en forme conservée du livre (positions en caractères dans [RichBook.text]). */
enum class SpanKind { BOLD, ITALIC, UNDERLINE, STRIKE, MONO, SUPER, SUB, SIZE }

data class RichSpan(val start: Int, val end: Int, val kind: SpanKind, val value: Float = 0f)

sealed interface LinkTarget {
    /** Adresse web, e-mail, téléphone… à ouvrir hors de l'app. */
    data class External(val url: String) : LinkTarget

    /** Passage du même livre (note de bas de page, renvoi, table des matières). */
    data class Internal(val offset: Int) : LinkTarget
}

data class LinkSpan(val start: Int, val end: Int, val target: LinkTarget)

/** Texte complet d'un livre + styles + liens. Les décalages servent aussi à la progression et aux annotations. */
/** Entrée du sommaire : [level] 0 = chapitre, 1 = sous-section… */
data class TocEntry(val title: String, val offset: Int, val level: Int)

class RichBook(
    val text: String,
    val spans: List<RichSpan>,
    val links: List<LinkSpan>,
    val toc: List<TocEntry> = emptyList()
)

private val URL_REGEX = Regex("https?://[^\\s<>\"]+")

/** Transforme en liens les adresses web écrites en clair, sauf celles déjà couvertes par un lien. */
fun autoLinks(text: String, existing: List<LinkSpan>): List<LinkSpan> {
    val out = ArrayList<LinkSpan>()
    for (m in URL_REGEX.findAll(text)) {
        val start = m.range.first
        var end = m.range.last + 1
        while (end > start && text[end - 1] in ".,;:!?)]}'\"»") end--
        if (end - start < 8) continue
        if (existing.any { it.start < end && it.end > start }) continue
        out += LinkSpan(start, end, LinkTarget.External(text.substring(start, end)))
    }
    return out
}

private val TXT_HEADING = Regex(
    "(?im)^(?:(?:chapitre|chapter|partie|part|livre|book|acte|act)\\s+[\\divxlcIVXLC]+\\b.{0,50}|prologue|épilogue|epilogue|préface|preface|introduction|conclusion)\\s*$"
)

/** Sommaire de secours pour un texte brut : lignes du type « Chapitre 3 », « Prologue »… */
fun txtToc(text: String): List<TocEntry> =
    TXT_HEADING.findAll(text).take(300).map { TocEntry(it.value.trim(), it.range.first, 0) }.toList()
