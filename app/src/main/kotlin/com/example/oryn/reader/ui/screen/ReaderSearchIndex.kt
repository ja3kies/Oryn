package com.example.oryn.reader.ui.screen

/** Search offsets refer to the original document; never normalize its text before jumping. */
internal data class ReaderSearchMatch(val offset: Int, val length: Int)

internal fun findReaderMatches(
    text: String, query: String, limit: Int = 200, checkCancellation: () -> Unit = {}
): List<ReaderSearchMatch> {
    val needle = query.trim()
    if (needle.length > text.length) return emptyList()
    if (needle.length < 2 || limit <= 0 || text.isEmpty()) return emptyList()
    val matches = ArrayList<ReaderSearchMatch>()
    var from = 0
    while (from <= text.length - needle.length && matches.size < limit) {
        // Unlike String.indexOf, this bounded scan can abandon obsolete queries even
        // when a multi-megabyte document contains no match. Offsets remain UTF-16.
        if (from % 1024 == 0) checkCancellation()
        if (text.regionMatches(from, needle, 0, needle.length, ignoreCase = true)) {
            matches.add(ReaderSearchMatch(from, needle.length))
            from += needle.length
            checkCancellation()
        } else {
            from++
        }
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
