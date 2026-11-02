package com.example.oryn.reader.community.auth

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Hermetic HTTPS protocol tests: loopback server, generated test CA, no Supabase account. */
class SupabaseAuthApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: SupabaseAuthApi
    private lateinit var client: OkHttpClient

    @Before fun start() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        client = SupabaseAuthApi.hardenedClient().newBuilder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .addInterceptor { chain ->
                val request = chain.request()
                // Test-only route into the TLS loopback fixture; never present in the application.
                chain.proceed(request.newBuilder().url(request.url.newBuilder().host("localhost").port(server.port).build()).build())
            }.build()
        api = SupabaseAuthApi(enabledConfig(), client)
    }

    @After fun stop() { server.shutdown() }
    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun tokens() = """{"token_type":"bearer","access_token":"test-access","refresh_token":"test-refresh","expires_in":3600}"""

    @Test fun pkceExchangeSendsVerifierInBodyAndNeverInUrl() = runBlocking {
        server.enqueue(response(tokens()))
        val verifier = Pkce.create(1000).verifier
        assertTrue(api.exchange("test-code", verifier).access == "test-access")
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/auth/v1/token?grant_type=pkce", request.path)
        assertEquals("POST", request.method)
        val body = JSONObject(request.body.readUtf8())
        assertTrue(body.getString("code_verifier") == verifier)
        assertEquals("test-code", body.getString("auth_code"))
        assertNull(request.getHeader("Authorization"))
        assertTrue(request.getHeader("apikey") == enabledConfig().publishableKey)
        assertEquals("no-store", request.getHeader("Cache-Control"))
    }

    @Test fun refreshValidationAndLogoutUseExpectedProtocol() = runBlocking {
        server.enqueue(response(tokens()))
        server.enqueue(response("""{"id":"$testUser","is_anonymous":false}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        api.refresh("test-old-refresh")
        assertEquals(testUser, api.userId("test-access"))
        api.logout("test-access")
        val refresh = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=refresh_token", refresh.path)
        assertTrue(JSONObject(refresh.body.readUtf8()).getString("refresh_token") == "test-old-refresh")
        val user = server.takeRequest()
        assertEquals("/auth/v1/user", user.path)
        assertEquals("GET", user.method)
        assertTrue(user.getHeader("Authorization") == "Bearer test-access")
        val logout = server.takeRequest()
        assertEquals("/auth/v1/logout?scope=local", logout.path)
        assertEquals("POST", logout.method)
    }

    @Test fun rejectsRedirectWithoutForwardingCredentials() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://attacker.example.test/steal"))
        try { api.userId("test-access"); fail("Redirect followed") }
        catch (error: AuthHttpException) { assertEquals(302, error.status) }
        assertEquals(1, server.requestCount)
    }

    @Test fun rejectsAnonymousMalformedAndOversizedResponses() = runBlocking {
        for (body in listOf("""{"id":"$testUser","is_anonymous":true}""", "not-json", "x".repeat(65537))) {
            server.enqueue(response(body))
            try { api.userId("test-access"); fail("Invalid session accepted") }
            catch (_: InvalidAuthSession) { }
        }
    }

    @Test fun cancellationCancelsTheSuspendedHttpOperation() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch(start = CoroutineStart.UNDISPATCHED) { api.refresh("test-refresh") }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(1, server.requestCount)
    }

    @Test fun httpErrorsExposeOnlyStatusAndNoResponseDetails() = runBlocking {
        server.enqueue(response("private-server-details").setResponseCode(401))
        try { api.refresh("test-refresh"); fail("Invalid refresh accepted") }
        catch (error: AuthHttpException) {
            assertEquals(401, error.status)
            assertFalse(error.toString().contains("private-server-details"))
        }
    }
    @Test fun disabledTransportCannotIssueAnyRequest() = runBlocking {
        val disabled = SupabaseAuthApi(AuthConfiguration("", "", "", "", false, true, false))
        try { disabled.userId("test-access"); fail("Disabled Auth transport used") }
        catch (_: IllegalStateException) { }
        assertEquals(0, server.requestCount)
    }

    @Test fun timeoutAndDisconnectedSocketDoNotRetryTokenRequests() = runBlocking {
        val quickApi = SupabaseAuthApi(enabledConfig(), client.newBuilder()
            .readTimeout(150, TimeUnit.MILLISECONDS).callTimeout(3, TimeUnit.SECONDS).build())
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try { quickApi.refresh("test-refresh"); fail("Timeout accepted") }
        catch (_: java.io.IOException) { }
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        try { quickApi.refresh("test-refresh"); fail("Disconnected socket accepted") }
        catch (_: java.io.IOException) { }
        assertEquals(2, server.requestCount)
    }

    @Test fun untrustedHttpsCertificateIsRejectedWithoutTrustBypass() = runBlocking {
        val strict = SupabaseAuthApi.hardenedClient().newBuilder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
        }.build()
        server.enqueue(response(tokens()))
        try { SupabaseAuthApi(enabledConfig(), strict).refresh("test-refresh"); fail("Untrusted certificate accepted") }
        catch (_: javax.net.ssl.SSLException) { }
        assertEquals(0, server.requestCount)
    }

    @Test fun oversizedChunkedResponseIsBoundedWithoutContentLength() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setChunkedBody("x".repeat(65537), 4096))
        try { api.refresh("test-refresh"); fail("Oversized stream accepted") }
        catch (_: InvalidAuthSession) { }
        assertEquals(1, server.requestCount)
    }

}
