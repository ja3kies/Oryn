package com.example.oryn.reader.data.importer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Reclaim an acquired document if dispatch back to its UI owner is cancelled. */
internal suspend fun <T : Any> loadDocumentWithCleanup(load: () -> T, dispose: (T) -> Unit): T {
    var acquired: T? = null
    try {
        return withContext(Dispatchers.IO) { load().also { acquired = it } }
    } catch (cancelled: CancellationException) {
        withContext(NonCancellable + Dispatchers.IO) { acquired?.let(dispose) }
        throw cancelled
    }
}
