package com.example.oryn.reader.ui.screen

import org.junit.Assert.*
import org.junit.Test

class ReaderSearchIndexTest {
    @Test fun matchesPreserveOriginalOffsetsAndIgnoreCase() {
        assertEquals(listOf(ReaderSearchMatch(0, 4), ReaderSearchMatch(11, 4)),
            findReaderMatches("Book about BOOKS", "book"))
    }
    @Test fun searchHasMinimumLengthAndBoundedResults() {
        assertTrue(findReaderMatches("hello", "h").isEmpty())
        assertTrue(findReaderMatches("hello", "   ").isEmpty())
        assertTrue(findReaderMatches("hello", "he", 0).isEmpty())
        assertEquals(2, findReaderMatches("aa aa aa aa", "aa", 2).size)
    }
    @Test fun startPositionWrapsAtEnd() {
        val matches = findReaderMatches("book x book x book", "book")
        assertEquals(1, readerSearchStartIndex(matches, 3))
        assertEquals(0, readerSearchStartIndex(matches, 100))
        assertEquals(0, readerSearchStartIndex(matches, 0))
        assertEquals(-1, readerSearchStartIndex(emptyList(), 0))
    }
    @Test fun searchHandlesOversizedQueryAndOverlappingText() {\n        assertTrue(findReaderMatches("abc", "abcdefgh").isEmpty())\n        assertEquals(listOf(ReaderSearchMatch(0, 2), ReaderSearchMatch(2, 2)), findReaderMatches("aaaa", "aa"))\n    }\n    @Test fun previousAndNextWrapAndHandleEmptyResults() {
        assertEquals(2, readerSearchStep(0, 3, -1))
        assertEquals(0, readerSearchStep(2, 3, 1))
        assertEquals(-1, readerSearchStep(0, 0, 1))
    }
}
