package com.example.oryn.reader.community.edge

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class CommunityEdgeHttpTest {
    private lateinit var fixture: EdgeTestFixture
    @Before fun start() { fixture = EdgeTestFixture() }
    @After fun end() { fixture.close() }

    @Test fun creationUsesExactWhitelistedEndpointAndExplicitRightsBody() = runBlocking {
        fixture.server.enqueue(fixture.json("""{"bookId":"$edgeBookId","bucket":"community-epubs","objectPath":"$edgeOwnerId/$edgeBookId.epub","uploadExpiresAt":"2099-01-02T03:04:05.123456+00:00","maxFileBytes":8388608,"status":"pending"}""", 201))
        val result = fixture.repository.createUpload(createInput()) as CommunityEdgeResult.Success
        assertEquals(edgeBookId, result.value.bookId)
        assertFalse(result.value.toString().contains(edgeOwnerId.toString()))
        val request = fixture.server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/functions/v1/catalog-create-upload", request.path)
        assertTrue(request.getHeader("Authorization") == "Bearer $edgeToken")
        assertTrue(request.getHeader("apikey") == edgeKey)
        assertEquals("no-store", request.getHeader("Cache-Control"))
        val body = request.body.readUtf8()
        assertFalse(body.contains("private-local-name"))
        val json = JSONObject(body)
        assertEquals(setOf("title", "author", "language", "rightsConfirmed"), json.keys().asSequence().toSet())
        assertTrue(json.getBoolean("rightsConfirmed"))
    }

    @Test fun finalizationStaysPendingAndRetractionDistinguishesDeferredCleanup() = runBlocking {
        fixture.server.enqueue(fixture.finalized())
        val final = fixture.repository.finalizeUpload(edgeBookId) as CommunityEdgeResult.Success
        assertEquals(edgeBookId, final.value.bookId)
        val request = fixture.server.takeRequest()
        assertEquals("/functions/v1/catalog-finalize-upload", request.path)
        assertEquals(setOf("bookId"), JSONObject(request.body.readUtf8()).keys().asSequence().toSet())
        for ((status, deleted) in listOf(200 to true, 202 to false)) {
            fixture.server.enqueue(fixture.json("""{"bookId":"$edgeBookId","status":"removed","objectDeleted":$deleted,"cleanupPending":${!deleted}}""", status))
            val result = fixture.repository.retract(edgeBookId) as CommunityEdgeResult.Success
            assertEquals(deleted, result.value.objectDeleted)
            assertEquals(!deleted, result.value.cleanupPending)
            assertEquals("/functions/v1/catalog-retract", fixture.server.takeRequest().path)
        }
    }

    @Test fun redirectsNeverForwardTokensToAnotherAuthority() = runBlocking {
        val attacker = MockWebServer().apply { start() }
        try {
            fixture.server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", attacker.url("/steal")))
            val result = fixture.repository.finalizeUpload(edgeBookId) as CommunityEdgeResult.Failure
            assertEquals(CommunityEdgeFailureCode.InvalidResponse, result.code)
            assertTrue(result.outcomeMayHaveChanged)
            assertEquals(1, fixture.server.requestCount)
            assertEquals(0, attacker.requestCount)
        } finally { attacker.shutdown() }
    }

    @Test fun cancellationAndSocketCutsDoNotRetryMutations() = runBlocking {
        fixture.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch(start = CoroutineStart.UNDISPATCHED) { fixture.repository.finalizeUpload(edgeBookId) }
        assertNotNull(fixture.server.takeRequest(5, TimeUnit.SECONDS))
        job.cancelAndJoin(); assertTrue(job.isCancelled)
        fixture.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val result = fixture.repository.finalizeUpload(edgeBookId) as CommunityEdgeResult.Failure
        assertEquals(CommunityEdgeFailureCode.NetworkUnavailable, result.code)
        assertTrue(result.outcomeMayHaveChanged)
        assertEquals(2, fixture.server.requestCount)
    }

    @Test fun timeoutIsReportedWithoutReplay() = runBlocking {
        val quick = fixture.client.newBuilder().readTimeout(150, TimeUnit.MILLISECONDS).callTimeout(3, TimeUnit.SECONDS).build()
        val core = CommunityEdgeOperationsCore(CommunityEdgeProtocol(HttpsCommunityEdgeHttp(fixture.origin, quick)), fixture.session)
        fixture.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = core.finalizeUpload(edgeBookId) as CommunityEdgeResult.Failure
        assertEquals(CommunityEdgeFailureCode.NetworkUnavailable, result.code)
        assertTrue(result.outcomeMayHaveChanged)
        assertEquals(1, fixture.server.requestCount)
    }

    @Test fun binaryDownloadIsBoundedAndHashCheckedWithoutTrustingServerFilename() = runBlocking {
        fixture.server.enqueue(MockResponse().setHeader("Content-Type", "application/epub+zip")
            .setHeader("Content-Disposition", "attachment; filename=../../private.epub").setBody(okio.Buffer().write(edgeBytes)))
        val result = fixture.repository.download(downloadPlan()) as CommunityEdgeResult.Success
        assertArrayEquals(edgeBytes, result.value.copyBytes())
        result.value.close()
        assertTrue(result.value.copyBytes().all { it == 0.toByte() })
        val request = fixture.server.takeRequest()
        assertEquals("/functions/v1/catalog-download", request.path)
        assertEquals("application/epub+zip", request.getHeader("Accept"))
        assertEquals(setOf("bookId"), JSONObject(request.body.readUtf8()).keys().asSequence().toSet())
    }

    @Test fun rejectsWrongHashMimeAndOversizedChunkedBinary() = runBlocking {
        fixture.server.enqueue(MockResponse().setHeader("Content-Type", "application/epub+zip")
            .setBody(okio.Buffer().write(edgeBytes.copyOf().also { it[it.lastIndex] = 0 })))
        fixture.server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("not epub"))
        fixture.server.enqueue(MockResponse().setHeader("Content-Type", "application/epub+zip")
            .setChunkedBody(okio.Buffer().write(edgeBytes + byteArrayOf(1)), 8))
        repeat(3) {
            val result = fixture.repository.download(downloadPlan()) as CommunityEdgeResult.Failure
            assertEquals(CommunityEdgeFailureCode.InvalidResponse, result.code)
        }
    }

    @Test fun rejectsMalformedOrContradictorySuccessResponsesAndErrorBodiesStayPrivate() = runBlocking {
        for (body in listOf("not-json", """{"bookId":"$edgeBookId","status":"published","validated":true,"sha256":"${"a".repeat(64)}"}""")) {
            fixture.server.enqueue(fixture.json(body))
            assertEquals(CommunityEdgeFailureCode.InvalidResponse,
                (fixture.repository.finalizeUpload(edgeBookId) as CommunityEdgeResult.Failure).code)
        }
        fixture.server.enqueue(fixture.json("private-server-error-and-user-data", 500))
        val result = fixture.repository.finalizeUpload(edgeBookId)
        assertFalse(result.toString().contains("private-server-error"))
        assertFalse(result.toString().contains(edgeToken))
    }

    @Test fun rejectsUnsafeOriginsTokensAndUnsafeInjectedHttpClientsBeforeIo() {
        for (origin in listOf("http://example.test", "https://user@example.test", "https://example.test/path",
            "https://example.test?query=token", "https://example.test#fragment", "https://%65xample.test", "https://example.test:444")) {
            expectInvalid { CommunityEdgeOrigin(origin, edgeKey) }
        }
        expectInvalid { CommunityUserToken(edgeKey) }
        expectInvalid { CommunityUserToken("sb_secret_invalid_fixture") }
        expectInvalid { HttpsCommunityEdgeHttp(fixture.origin, fixture.client.newBuilder().followRedirects(true).build()) }
        expectInvalid { HttpsCommunityEdgeHttp(fixture.origin, fixture.client.newBuilder().addInterceptor { it.proceed(it.request()) }.build()) }
        assertEquals(0, fixture.server.requestCount)
        assertEquals(setOf("catalog-create-upload", "catalog-finalize-upload", "catalog-retract", "catalog-download", "catalog-moderate"),
            UserCommunityFunction.entries.map { it.path }.toSet())
    }

    @Test fun untrustedCertificateCannotReceiveAnAuthenticatedHttpRequest() = runBlocking {
        val strict = CommunityEdgeOperationsCore(CommunityEdgeProtocol(HttpsCommunityEdgeHttp(fixture.origin)), fixture.session)
        fixture.server.enqueue(fixture.finalized())
        val result = strict.finalizeUpload(edgeBookId) as CommunityEdgeResult.Failure
        assertEquals(CommunityEdgeFailureCode.NetworkUnavailable, result.code)
        assertEquals(0, fixture.server.requestCount)
    }

    private fun expectInvalid(block: () -> Any) {
        try { block(); fail("Unsafe protocol input accepted") } catch (_: IllegalArgumentException) { }
    }
}
