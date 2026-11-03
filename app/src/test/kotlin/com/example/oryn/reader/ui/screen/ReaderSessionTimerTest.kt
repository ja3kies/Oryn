package com.example.oryn.reader.ui.screen

import org.junit.Assert.*
import org.junit.Test

class ReaderSessionTimerTest {
    @Test fun recordsMeasuredForegroundIntervalsAndPausesWithoutInventingTime() {
        val timer = ReaderSessionTimer()
        timer.record(1250)
        timer.running = false
        timer.record(5000)
        assertEquals(1250L, timer.elapsedMillis)
        timer.running = true
        timer.record(-10)
        timer.record(750)
        assertEquals(2000L, timer.elapsedMillis)
    }
    @Test fun restorationAndResetPreserveTheExplicitRunningChoice() {
        val restored = ReaderSessionTimer(9000, false)
        assertEquals(9000L, restored.elapsedMillis)
        assertFalse(restored.running)
        restored.reset()
        assertEquals(0L, restored.elapsedMillis)
        assertFalse(restored.running)
    }
}
