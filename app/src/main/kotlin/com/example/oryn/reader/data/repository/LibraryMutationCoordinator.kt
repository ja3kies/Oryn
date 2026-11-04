package com.example.oryn.reader.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Protects the joint lifetime of private files and their Room references within this application. */
internal class LibraryMutationCoordinator {
    private val mutex = Mutex()
    suspend fun <T> mutate(block: suspend () -> T): T = mutex.withLock { block() }
}
