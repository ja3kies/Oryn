package com.example.oryn.reader.community.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** GoTrue REST protocol, compatible with API 24 and existing OkHttp. No SDK/version mismatch.
 * No interceptors, logging, cookies, disk cache, redirects, or automatic refresh retries.
 */
class SupabaseAuthApi internal constructor(
    private val config: AuthConfiguration,
    private val client: OkHttpClient
) : AuthApi {
    constructor(config: AuthConfiguration) : this(config, hardenedClient())
    override suspend fun exchange(code: String, verifier: String): AuthTokens = tokens(request(
        "/token?grant_type=pkce", JSONObject().put("auth_code", code).put("code_verifier", verifier)
    ))

    override suspend fun refresh(refreshToken: String): AuthTokens = tokens(request(
        "/token?grant_type=refresh_token", JSONObject().put("refresh_token", refreshToken)
    ))

    override suspend fun userId(accessToken: String): UUID {
        val raw = request("/user", token = accessToken)
        return try {
            val json = JSONObject(raw)
            if (json.optBoolean("is_anonymous", true)) throw InvalidAuthSession()
            UUID.fromString(json.getString("id"))
        } catch (_: Exception) { throw InvalidAuthSession() }
    }

    override suspend fun logout(accessToken: String) {
        request("/logout?scope=local", JSONObject(), accessToken, emptyResponse = true)
    }

    private fun tokens(raw: String): AuthTokens {
        try {
            val json = JSONObject(raw)
            if (!json.getString("token_type").equals("bearer", ignoreCase = true)) throw InvalidAuthSession()
            return AuthTokens(json.getString("access_token"), json.getString("refresh_token"), json.getLong("expires_in"))
        } catch (_: Exception) { throw InvalidAuthSession() }
    }

    private suspend fun request(
        path: String, body: JSONObject? = null, token: String? = null, emptyResponse: Boolean = false
    ): String {
        check(config.isEnabled) { "Auth disabled" }
        require(token == null || (token.length in 1..8192 && token.none(Char::isWhitespace)))
        val builder = Request.Builder().url(config.origin.trimEnd('/') + "/auth/v1" + path)
            .header("apikey", config.publishableKey).header("Accept", "application/json")
            .header("Cache-Control", "no-store")
        token?.let { builder.header("Authorization", "Bearer $it") }
        body?.let { builder.post(it.toString().toRequestBody("application/json".toMediaType())) }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try { continuation.resume(read(response, emptyResponse)) }
                    catch (error: Exception) { continuation.resumeWithException(error) }
                }
            })
        }
    }

    private fun read(response: Response, emptyResponse: Boolean): String = response.use {
        if (!it.isSuccessful) throw AuthHttpException(it.code)
        if (emptyResponse) return@use ""
        if (!it.header("Content-Type").orEmpty().substringBefore(';').trim().equals("application/json", true)) {
            throw InvalidAuthSession()
        }
        val body = it.body ?: throw InvalidAuthSession()
        if (body.contentLength() > MAX_BYTES) throw InvalidAuthSession()
        body.byteStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_BYTES) throw InvalidAuthSession()
                output.write(buffer, 0, count)
            }
            output.toString("UTF-8")
        }
    }

    companion object {
        private const val MAX_BYTES = 64 * 1024
        internal fun hardenedClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS).build()
    }
}
