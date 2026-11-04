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
    @Test fun searchHandlesOversizedQueryAndAdjacentNonOverlappingMatches() {
        assertTrue(findReaderMatches("abc", "abcdefgh").isEmpty())
        assertEquals(
            listOf(ReaderSearchMatch(0, 2), ReaderSearchMatch(2, 2)),
            findReaderMatches("aaaa", "aa")
        )
    }
    @Test fun navigationDoesNotOverflowAtIntegerBounds() {
        assertEquals(0, readerSearchStep(Int.MAX_VALUE, 2, Int.MAX_VALUE))
        assertEquals(0, readerSearchStep(Int.MIN_VALUE, 2, Int.MIN_VALUE))
    }
    @Test fun previousAndNextWrapAndHandleEmptyResults() {
        assertEquals(2, readerSearchStep(0, 3, -1))
        assertEquals(0, readerSearchStep(2, 3, 1))
        assertEquals(-1, readerSearchStep(0, 0, 1))
    }
    @Test fun abandonedSearchWithoutMatchesStopsWithinOneScanBlock() {
        var checks = 0
        val cancelled = java.util.concurrent.CancellationException("obsolete query")
        try {
            findReaderMatches("a".repeat(2_000_000), "zz", checkCancellation = {
                if (++checks == 2) throw cancelled
            })
            org.junit.Assert.fail("Search did not observe cancellation")
        } catch (error: java.util.concurrent.CancellationException) {
            org.junit.Assert.assertSame(cancelled, error)
        }
        org.junit.Assert.assertEquals(2, checks)
    }

}
