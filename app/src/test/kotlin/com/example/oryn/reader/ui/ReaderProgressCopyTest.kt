package com.example.oryn.reader.ui

import com.example.oryn.reader.i18n.trf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Display regression: trf is a simple %s substitution, not printf. */
class ReaderProgressCopyTest {
    @Test fun bookProgressHasExactlyOnePercentSign() {
        val label = trf("Livre : %s %", 3)
        assertEquals(1, label.count { it == '%' })
        assertTrue(label.endsWith("3 %"))
    }

    @Test fun chapterProgressHasExactlyOnePercentSign() {
        val label = trf("Chapitre : %s %", 10)
        assertEquals(1, label.count { it == '%' })
        assertTrue(label.endsWith("10 %"))
    }
}
