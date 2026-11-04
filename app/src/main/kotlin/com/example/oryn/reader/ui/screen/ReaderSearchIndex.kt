package com.example.oryn.reader.ui.screen

/** Search offsets refer to the original document; never normalize its text before jumping. */
internal data class ReaderSearchMatch(val offset: Int, val length: Int)

internal fun findReaderMatches(
    text: String, query: String, limit: Int = 200, checkCancellation: () -> Unit = {}
): List<ReaderSearchMatch> {
    val needle = query.trim()
    if (needle.length > text.length) return emptyList()
    if (needle.length < 2 || limit <= 0 || text.isEmpty()) return emptyList()
    // KMP compares simple Unicode case folds without rebuilding/normalizing the book.
    // One scan block is bounded in codepoints, not potentially huge regionMatches calls.
    fun fold(codePoint: Int): Int = Character.toLowerCase(Character.toUpperCase(codePoint))
    val pattern = IntArray(needle.codePointCount(0, needle.length))
    var source = 0
    for (i in pattern.indices) {
        if (i % 1024 == 0) checkCancellation()
        val point = needle.codePointAt(source)
        pattern[i] = fold(point)
        source += Character.charCount(point)
    }
    val prefix = IntArray(pattern.size)
    for (i in 1 until pattern.size) {
        if (i % 1024 == 0) checkCancellation()
        var matched = prefix[i - 1]
        while (matched > 0 && pattern[i] != pattern[matched]) matched = prefix[matched - 1]
        if (pattern[i] == pattern[matched]) matched++
        prefix[i] = matched
    }
    val matches = ArrayList<ReaderSearchMatch>()
    val offsets = IntArray(pattern.size)
    var cursor = 0
    var scanned = 0
    var matched = 0
    while (cursor < text.length && matches.size < limit) {
        if (scanned % 1024 == 0) checkCancellation()
        offsets[scanned % offsets.size] = cursor
        val point = text.codePointAt(cursor)
        val folded = fold(point)
        while (matched > 0 && folded != pattern[matched]) matched = prefix[matched - 1]
        if (folded == pattern[matched]) matched++
        cursor += Character.charCount(point)
        if (matched == pattern.size) {
            val start = offsets[(scanned - pattern.size + 1) % offsets.size]
            // Keep persisted offsets and match lengths in the original UTF-16 document.
            matches.add(ReaderSearchMatch(start, cursor - start))
            matched = 0 // Existing search semantics do not materialize overlapping hits.
        }
        scanned++
    }
    return matches
}

/** Nearest match at or after the reading position, wrapping to the first result. */
internal fun readerSearchStartIndex(matches: List<ReaderSearchMatch>, position: Int): Int {
    if (matches.isEmpty()) return -1
    val next = matches.binarySearch { it.offset.compareTo(position) }
    return if (next >= 0) next else (-next - 1).takeIf { it < matches.size } ?: 0
}

internal fun readerSearchStep(index: Int, count: Int, delta: Int): Int =
    if (count <= 0) -1 else Math.floorMod(index.toLong() + delta.toLong(), count.toLong()).toInt()
