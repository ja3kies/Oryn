package com.example.oryn.reader.ui.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import com.example.oryn.reader.data.importer.RichBook
import com.example.oryn.reader.data.importer.SpanKind

/** Texte du livre avec ses styles et ses liens (colorés et soulignés). */
internal fun RichBook.toAnnotatedString(linkColor: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val max = text.length
    for (s in spans) {
        val style = when (s.kind) {
            SpanKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            SpanKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            SpanKind.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
            SpanKind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            SpanKind.MONO -> SpanStyle(fontFamily = FontFamily.Monospace)
            SpanKind.SUPER -> SpanStyle(baselineShift = BaselineShift.Superscript)
            SpanKind.SUB -> SpanStyle(baselineShift = BaselineShift.Subscript)
            // Titres : agrandis, mais sans dépasser la hauteur de ligne
            SpanKind.SIZE -> SpanStyle(fontSize = s.value.coerceIn(0.5f, 1.35f).em)
        }
        val a = s.start.coerceIn(0, max)
        val b = s.end.coerceIn(0, max)
        if (b > a) addStyle(style, a, b)
    }
    val linkStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    for (l in links) {
        val a = l.start.coerceIn(0, max)
        val b = l.end.coerceIn(0, max)
        if (b > a) addStyle(linkStyle, a, b)
    }
}

/** Début du passage visé par une note de bas de page (jusqu'à la fin du paragraphe, 700 caractères max). */
internal fun footnotePreview(text: String, offset: Int): String? {
    var start = offset.coerceIn(0, text.length)
    while (start < text.length && text[start].isWhitespace()) start++
    val paragraphEnd = text.indexOf("\n\n", start).let { if (it < 0) text.length else it }
    val end = minOf(paragraphEnd, start + 700)
    return text.substring(start, end).trim().takeIf { it.length >= 3 }
}
