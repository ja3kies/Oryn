package com.example.oryn.reader.community.edge

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellableContinuation
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Closed endpoint allowlist; there is deliberately no arbitrary path or server cleanup entry. */
internal enum class UserCommunityFunction(val path: String) {
    Create("catalog-create-upload"), Finalize("catalog-finalize-upload"), Retract("catalog-retract"),
    Download("catalog-download"), Moderate("catalog-moderate")
}
internal class CommunityUserToken(val value: String) {
    init { require(value.length in 1..8192 && value.matches(Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))) }
    override fun toString() = "CommunityUserToken([redacted])"
}

/** Internal protocol origin. Production construction additionally uses the stricter application gate.
 * Explicit HTTPS loopback ports support hermetic tests without rewriting requests or disabling TLS.
 */
internal class CommunityEdgeOrigin(origin: String, val publishableKey: String) {
    val base: HttpUrl = origin.toHttpUrl()
    init {
        require(base.scheme == "https" && base.username.isEmpty() && base.password.isEmpty())
        require(base.encodedPath == "/" && base.query == null && base.fragment == null)
        require(base.port == 443 || base.host in setOf("localhost", "127.0.0.1", "::1"))
        require(publishableKey.matches(Regex("sb_publishable_[A-Za-z0-9_-]{10,200}")))
        // HttpUrl canonicalization must not turn credentials, paths or fragments into a trusted origin.
        val raw = java.net.URI(origin)
        require(raw.scheme == "https" && !raw.host.isNullOrBlank() && raw.rawUserInfo == null && raw.rawQuery == null && raw.rawFragment == null)
        require(raw.rawPath.orEmpty() in listOf("", "/"))
    }
    fun endpoint(function: UserCommunityFunction): HttpUrl = base.newBuilder()
        .addPathSegments("functions/v1/" + function.path).build()
    override fun toString() = "CommunityEdgeOrigin([redacted])"
}

internal interface CommunityEdgeHttp {
    suspend fun post(function: UserCommunityFunction, token: CommunityUserToken, body: ByteArray,
        statuses: Set<Int>, responseMime: String, maxBytes: Int): CommunityEdgeHttpBody
}
internal class CommunityEdgeHttpBody(val status: Int, val bytes: ByteArray) {
    override fun toString() = "CommunityEdgeHttpBody([redacted])"
}

/** Ownership crosses a cancellable dispatcher hop. Undelivered buffers must be discarded. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun CancellableContinuation<CommunityEdgeHttpBody>.resumeOwned(body: CommunityEdgeHttpBody) {
    resume(body) { body.bytes.fill(0) }
}

/** Internal HTTP adapter: only the guarded production factory wires this into the app.
 * Independently tested against a TLS loopback server. Never logs request/response content.
 */
internal class HttpsCommunityEdgeHttp(
    private val origin: CommunityEdgeOrigin,
    private val client: OkHttpClient = hardenedClient()
) : CommunityEdgeHttp {
    init {
        require(!client.followRedirects && !client.followSslRedirects && !client.retryOnConnectionFailure)
        require(client.interceptors.isEmpty() && client.networkInterceptors.isEmpty())
        require(client.cache == null && client.cookieJar === CookieJar.NO_COOKIES)
        require(client.authenticator === Authenticator.NONE && client.proxyAuthenticator === Authenticator.NONE)
    }
    override suspend fun post(
        function: UserCommunityFunction, token: CommunityUserToken, body: ByteArray,
        statuses: Set<Int>, responseMime: String, maxBytes: Int
    ): CommunityEdgeHttpBody {
        require(body.size in 1..8192 && maxBytes in 1..(8 * 1024 * 1024))
        val url = origin.endpoint(function)
        // Assert the authority before attaching credentials, even if endpoint construction changes later.
        check(url.scheme == "https" && url.host == origin.base.host && url.port == origin.base.port &&
            url.encodedPath == "/functions/v1/" + function.path && url.query == null && url.fragment == null)
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer ${token.value}")
            .header("apikey", origin.publishableKey)
            .header("Accept", responseMime).header("Cache-Control", "no-store")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try { continuation.resumeOwned(read(response, statuses, responseMime, maxBytes)) }
                    catch (error: Exception) { continuation.resumeWithException(error) }
                }
            })
        }
    }
    private fun read(response: Response, statuses: Set<Int>, mime: String, maxBytes: Int): CommunityEdgeHttpBody = response.use {
        if (it.code !in statuses) {
            if (it.isSuccessful) throw InvalidCommunityEdgeResponse()
            val retry = it.header("Retry-After")?.toIntOrNull()?.takeIf { value -> value in 0..86400 }
            // Do not read/log untrusted error bodies or follow Location headers.
            throw CommunityEdgeHttpException(it.code, retry)
        }
        if (!it.header("Content-Type").orEmpty().substringBefore(';').trim().equals(mime, true)) throw InvalidCommunityEdgeResponse()
        val body = it.body ?: throw InvalidCommunityEdgeResponse()
        if (body.contentLength() > maxBytes) throw InvalidCommunityEdgeResponse()
        body.byteStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > maxBytes) throw InvalidCommunityEdgeResponse()
                output.write(buffer, 0, count)
            }
            CommunityEdgeHttpBody(it.code, output.toByteArray())
        }
    }
    companion object {
        internal fun hardenedClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .cookieJar(CookieJar.NO_COOKIES).cache(null)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS).build()
    }
}
