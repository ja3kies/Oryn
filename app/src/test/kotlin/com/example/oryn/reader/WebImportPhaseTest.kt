package com.example.oryn.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Completion must only be shown after Room accepted the imported book. */
class WebImportPhaseTest {
    @Test fun onlyInFlightPhasesBlockUserActions() {
        assertTrue(WebImportPhase.DOWNLOADING.inProgress)
        assertTrue(WebImportPhase.IMPORTING.inProgress)
        assertFalse(WebImportPhase.SUCCEEDED.inProgress)
        assertFalse(WebImportPhase.FAILED.inProgress)
    }

    @Test fun successAndFailureAreDistinctTerminalStates() {
        assertTrue(WebImportPhase.SUCCEEDED != WebImportPhase.FAILED)
        assertTrue(WebImportPhase.entries.size == 4)
    }
}
