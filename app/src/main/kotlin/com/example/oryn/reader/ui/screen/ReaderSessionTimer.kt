package com.example.oryn.reader.ui.screen

import android.os.SystemClock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.oryn.reader.i18n.Text
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import kotlinx.coroutines.delay

/** Measured foreground session duration, never a remaining-time estimate or a persisted statistic. */
internal class ReaderSessionTimer(initialElapsed: Long = 0, initialRunning: Boolean = true) {
    var elapsedMillis: Long = initialElapsed
        private set
    var running by mutableStateOf(initialRunning)
    fun record(delta: Long) { if (running && delta > 0) elapsedMillis += delta }
    fun reset() { elapsedMillis = 0 }
    companion object {
        val Saver = listSaver<ReaderSessionTimer, Any>(
            save = { listOf(it.elapsedMillis, it.running) },
            restore = { ReaderSessionTimer(it[0] as Long, it[1] as Boolean) }
        )
    }
}

@Composable
internal fun TrackReaderSession(timer: ReaderSessionTimer) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(timer, owner, timer.running) {
        if (timer.running) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = SystemClock.elapsedRealtime()
            try {
                while (true) {
                    delay(1000)
                    val now = SystemClock.elapsedRealtime()
                    timer.record(now - previous)
                    previous = now
                }
            } finally { timer.record(SystemClock.elapsedRealtime() - previous) }
        }
    }
}

@Composable
internal fun ReaderTimerDialog(timer: ReaderSessionTimer, onDismiss: () -> Unit) {
    var elapsed by remember { mutableLongStateOf(timer.elapsedMillis) }
    LaunchedEffect(timer) { while (true) { elapsed = timer.elapsedMillis; delay(250) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Chronomètre de lecture")) },
        text = { Text(trf("Temps dans cette session : %s min %s s", elapsed / 60_000, (elapsed / 1000) % 60)) },
        confirmButton = { TextButton(onClick = { timer.running = !timer.running }) { Text(tr(if (timer.running) "Pause" else "Reprendre")) } },
        dismissButton = {
            TextButton(onClick = { timer.reset(); elapsed = 0 }) { Text(tr("Réinitialiser")) }
            TextButton(onClick = onDismiss) { Text(tr("Fermer")) }
        }
    )
}
