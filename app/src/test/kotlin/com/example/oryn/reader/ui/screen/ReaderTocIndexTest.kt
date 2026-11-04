package com.example.oryn.reader.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTocIndexTest {
    @Test fun noChapterBeforeFirstOffset() {
        assertEquals(-1, readerCurrentTocIndex(listOf(10, 20), 0))
        assertEquals(-1, readerCurrentTocIndex(emptyList(), 100))
    }
    @Test fun selectsNearestChapterInUnsortedToc() {
        assertEquals(0, readerCurrentTocIndex(listOf(50, 0, 20), 55))
        assertEquals(2, readerCurrentTocIndex(listOf(50, 0, 20), 30))
    }
    @Test fun ignoresNegativeOffsetsAndKeepsFirstDuplicate() {
        assertEquals(1, readerCurrentTocIndex(listOf(-5, 0, 0, 20), 10))
        assertEquals(-1, readerCurrentTocIndex(listOf(-5, -1), 100))
    }
}
