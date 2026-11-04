package com.example.oryn.reader.data.remote

import java.text.Normalizer

private fun normalizeBookMetadata(value: String): String =
    Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/** Open Library's search is fuzzy. Never attach an unrelated partial-title hit to a book. */
internal fun sameOpenLibraryTitle(wanted: String, found: String): Boolean {
    val expected = normalizeBookMetadata(wanted)
    return expected.isNotEmpty() && expected == normalizeBookMetadata(found)
}

/**
 * A catalogue name may be "First Last" while the library metadata is "Last, First".
 * Match a whole family-name token rather than a shared first name or substring.
 * When transliterations are incompatible, omit remote metadata instead of guessing.
 */
internal fun sameOpenLibraryAuthor(expected: String, found: String): Boolean {
    val familyName = if (',' in expected) expected.substringBefore(',') else expected.substringAfterLast(' ')
    val token = normalizeBookMetadata(familyName)
    return token.length >= 3 && normalizeBookMetadata(found).split(' ').contains(token)
}
