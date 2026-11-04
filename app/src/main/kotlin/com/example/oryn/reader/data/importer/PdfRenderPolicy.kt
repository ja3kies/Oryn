package com.example.oryn.reader.data.importer

import kotlin.math.floor
import kotlin.math.sqrt

internal data class PdfRenderSize(val width: Int, val height: Int)

/** Bound native ARGB allocation even for a malformed or exceptionally tall PDF page. */
internal fun pdfRenderSize(pageWidth: Int, pageHeight: Int, requestedWidth: Int): PdfRenderSize? {
    if (pageWidth <= 0 || pageHeight <= 0 || requestedWidth <= 0) return null
    val width = requestedWidth.coerceAtMost(4096).toDouble()
    val height = width * pageHeight.toDouble() / pageWidth
    val scale = minOf(1.0, sqrt(4_000_000.0 / (width * height)), 8192.0 / height)
    return PdfRenderSize(floor(width * scale).toInt().coerceAtLeast(1), floor(height * scale).toInt().coerceAtLeast(1))
}
