package com.example.oryn.reader.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class LibraryMutationCoordinatorTest {
    @Test fun importReferencePublicationCompletesBeforeCoverReclamationChecks() = runTest {
        val coordinator = LibraryMutationCoordinator()
        val readStarted = CompletableDeferred<Unit>()
        val allowPublication = CompletableDeferred<Unit>()
        var references = 0
        var fileExists = true
        val importer = launch {
            coordinator.mutate {
                assertTrue(fileExists) // Cover path selected from filesystem cache.
                readStarted.complete(Unit)
                allowPublication.await()
                references++ // Room reference published after metadata work.
            }
        }
        readStarted.await()
        val remover = launch { coordinator.mutate { if (references == 0) fileExists = false } }
        yield()
        assertTrue(fileExists)
        allowPublication.complete(Unit)
        importer.join()
        remover.join()
        assertEquals(1, references)
        assertTrue(fileExists)
    }

    @Test fun stalledTransferDoesNotBlockAnUnrelatedLibraryMutation() = runTest {
        val coordinator = LibraryMutationCoordinator()
        val transferStarted = CompletableDeferred<Unit>()
        val bytesAvailable = CompletableDeferred<String>()
        var published = false
        val download = launch {
            coordinator.stageThenMutate(
                stage = { transferStarted.complete(Unit); bytesAvailable.await() },
                publish = { assertEquals("verified bytes", it); published = true }
            )
        }
        transferStarted.await()
        assertEquals("deleted unrelated row", coordinator.mutate { "deleted unrelated row" })
        assertFalse(published)
        bytesAvailable.complete("verified bytes")
        download.join()
        assertTrue(published)
    }

    @Test fun cancelledMutationReleasesTheLockForSubsequentImports() = runTest {
        val coordinator = LibraryMutationCoordinator()
        val started = CompletableDeferred<Unit>()
        val cancelled = launch {
            coordinator.mutate { started.complete(Unit); CompletableDeferred<Unit>().await() }
        }
        started.await()
        cancelled.cancelAndJoin()
        assertEquals("imported", coordinator.mutate { "imported" })
    }

    @Test fun parallelMutationsCannotReadTheSameStaleSnapshot() = runTest {
        val coordinator = LibraryMutationCoordinator()
        var references = 0
        (1..30).map {
            async { coordinator.mutate { val old = references; yield(); references = old + 1 } }
        }.awaitAll()
        assertEquals(30, references)
    }
    @Test fun separateActivityCoordinatorsCannotReclaimAnUnpublishedSharedFile() = runTest {
        val firstActivity = LibraryMutationCoordinator()
        val secondActivity = LibraryMutationCoordinator()
        val started = CompletableDeferred<Unit>()
        val publish = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val importer = launch {
            firstActivity.mutate { started.complete(Unit); publish.await() }
        }
        started.await()
        val deletion = launch { secondActivity.mutate { secondEntered.complete(Unit) } }
        yield()
        assertFalse(secondEntered.isCompleted)
        publish.complete(Unit)
        importer.join()
        deletion.join()
        assertTrue(secondEntered.isCompleted)
    }

}
