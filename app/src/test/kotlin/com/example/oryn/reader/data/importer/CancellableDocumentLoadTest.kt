package com.example.oryn.reader.data.importer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class CancellableDocumentLoadTest {
    @Test fun cancellationAfterOpeningButBeforeDeliveryClosesTheAcquiredDocument() = runBlocking {
        val owner = Job()
        val document = Any()
        var closed: Any? = null
        try {
            withContext(owner) {
                loadDocumentWithCleanup(load = {
                    owner.cancel()
                    document
                }, dispose = { closed = it })
            }
            fail("Cancelled owner received a document")
        } catch (_: CancellationException) { }
        assertSame(document, closed)
    }
    @Test fun deliveredDocumentRemainsOpenForItsOwner() = runBlocking {
        var closed = false
        val document = Any()
        assertSame(document, loadDocumentWithCleanup(load = { document }, dispose = { closed = true }))
        assertFalse(closed)
    }
}
