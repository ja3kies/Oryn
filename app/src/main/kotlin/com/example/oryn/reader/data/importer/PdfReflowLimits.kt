package com.example.oryn.reader.data.importer

import java.io.IOException

internal object PdfReflowLimits {
    const val MAX_CHARACTERS = 8_000_000
    const val MAX_SPANS = 500_000
    const val MAX_PAGES = 100_000

    fun checkGrowth(characters: Int, addedCharacters: Int, spans: Int, addedSpans: Int) {
        if (characters < 0 || addedCharacters < 0 || spans < 0 || addedSpans < 0 ||
            characters.toLong() + addedCharacters > MAX_CHARACTERS ||
            spans.toLong() + addedSpans > MAX_SPANS) {
            throw IOException("PDF text mode exceeds its bounded document budget")
        }
    }
}
