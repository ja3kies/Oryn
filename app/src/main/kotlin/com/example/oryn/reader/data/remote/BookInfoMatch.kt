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
