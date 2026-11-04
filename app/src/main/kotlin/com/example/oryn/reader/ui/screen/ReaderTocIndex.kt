package com.example.oryn.reader.ui.screen

/** Select the last chapter at or before the position, even for unsorted TOCs. */
internal fun readerCurrentTocIndex(offsets: List<Int>, position: Int): Int {
    var bestIndex = -1
    var bestOffset = -1
    offsets.forEachIndexed { index, offset ->
        if (offset >= 0 && offset <= position && offset > bestOffset) {
            bestIndex = index
            bestOffset = offset
        }
    }
    return bestIndex
}
