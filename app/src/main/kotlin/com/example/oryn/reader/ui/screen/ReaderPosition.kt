package com.example.oryn.reader.ui.screen

/** Book progress stores the end of a PDF page; floating-point rounding must not rewind it. */
internal fun pdfPageAtProgress(progress: Float, pageCount: Int): Int {
    if (pageCount <= 0) return 0
    val fraction = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    return (Math.round(fraction * pageCount) - 1).coerceIn(0, pageCount - 1)
}
