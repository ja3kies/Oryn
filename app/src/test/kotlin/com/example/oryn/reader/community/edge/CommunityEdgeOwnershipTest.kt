package com.example.oryn.reader.community.edge

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CommunityEdgeOwnershipTest {
    @Test fun cancellationAfterHttpCompletionBeforeDispatchErasesUndeliveredBuffer() = runTest {
        lateinit var continuation: CancellableContinuation<CommunityEdgeHttpBody>
        var delivered = false
        val job = launch {
            suspendCancellableCoroutine<CommunityEdgeHttpBody> { continuation = it }
            delivered = true
        }
        runCurrent()
        val bytes = edgeBytes.copyOf()
        continuation.resumeOwned(CommunityEdgeHttpBody(200, bytes))
        job.cancel()
        runCurrent()
        assertFalse(delivered)
        assertArrayEquals(ByteArray(bytes.size), bytes)
    }

    @Test fun cancellationBeforeHttpCompletionAlsoErasesLateBuffer() = runTest {
        lateinit var continuation: CancellableContinuation<CommunityEdgeHttpBody>
        val job = launch { suspendCancellableCoroutine<CommunityEdgeHttpBody> { continuation = it } }
        runCurrent()
        job.cancel()
        val bytes = edgeBytes.copyOf()
        continuation.resumeOwned(CommunityEdgeHttpBody(200, bytes))
        runCurrent()
        assertArrayEquals(ByteArray(bytes.size), bytes)
    }

    @Test fun successfulHandoffPreservesBytesForIntegrityValidation() = runTest {
        lateinit var continuation: CancellableContinuation<CommunityEdgeHttpBody>
        var received: CommunityEdgeHttpBody? = null
        launch { received = suspendCancellableCoroutine { continuation = it } }
        runCurrent()
        val body = CommunityEdgeHttpBody(200, edgeBytes.copyOf())
        continuation.resumeOwned(body)
        runCurrent()
        assertSame(body, received)
        assertArrayEquals(edgeBytes, received!!.bytes)
    }
}
