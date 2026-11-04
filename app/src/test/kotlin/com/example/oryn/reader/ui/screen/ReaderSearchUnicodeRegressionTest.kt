package com.example.oryn.reader.ui.screen

import org.junit.Assert.*
import org.junit.Test

/** Offset checks use literal source positions, including supplementary UTF-16 characters. */
class ReaderSearchUnicodeRegressionTest {
    @Test fun supplementaryCharactersBeforeAndInsideMatchesRetainSourceOffsets() {
        val text = "📖 Préface 😀BOOK puis 😀book"
        val matches = findReaderMatches(text, "😀book")
        assertEquals(listOf(ReaderSearchMatch(11, 6), ReaderSearchMatch(23, 6)), matches)
        assertEquals(listOf("😀BOOK", "😀book"), matches.map { text.substring(it.offset, it.offset + it.length) })
    }

    @Test fun greekFinalSigmaCaseFoldingDoesNotRewriteDocumentOffsets() {
        val text = "ΟΣ οσ ος"
        assertEquals(listOf(ReaderSearchMatch(0, 2), ReaderSearchMatch(3, 2), ReaderSearchMatch(6, 2)),
            findReaderMatches(text, "οσ"))
    }

    @Test fun turkishDottedAndDotlessCharactersUseLengthPreservingCaseComparison() {
        val text = "İX ix ıx IX"
        assertEquals(listOf(0, 3, 6, 9), findReaderMatches(text, "ix").map { it.offset })
        assertTrue(findReaderMatches("straße", "STRASSE").isEmpty())
    }

    @Test fun canonicalNormalizationIsNotAppliedBehindTheUsersBack() {
        val text = "e\u0301cole école"
        assertEquals(listOf(ReaderSearchMatch(7, 2)), findReaderMatches(text, "éc"))
        assertEquals(listOf(ReaderSearchMatch(0, 3)), findReaderMatches(text, "e\u0301c"))
    }

    @Test fun trimmedQueryLengthIsUsedForSourceSelection() {
        val text = "📚 livre et LIVRE"
        assertEquals(listOf(ReaderSearchMatch(3, 5), ReaderSearchMatch(12, 5)),
            findReaderMatches(text, " \tLiVrE\n"))
    }

    @Test fun boundedDenseResultsStillNavigateAtTheVisibleResultBoundaries() {
        val matches = findReaderMatches("livre ".repeat(2000), "livre")
        assertEquals(200, matches.size)
        assertEquals(1194, matches.last().offset)
        assertEquals(199, readerSearchStartIndex(matches, 1194))
        assertEquals(0, readerSearchStartIndex(matches, 1195))
        assertEquals(0, readerSearchStep(199, matches.size, 1))
        assertEquals(199, readerSearchStep(0, matches.size, -1))
    }
}
