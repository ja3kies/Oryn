package com.example.oryn.reader.community

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CommunityHttpException(val status: Int) : IOException("Community HTTP $status")

/** HTTPS, no redirects/cookies/cache/logging; bounded metadata responses. No write endpoint. */
class SupabaseCommunityCatalogApi(private val configuration: CommunityConfiguration) : CommunityCatalogApi {
    private val client by lazy {
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS).build()
    }

    override suspend fun verifyUser(token: String): Boolean {
        val user = JSONObject(get("/auth/v1/user", token))
        return !user.optBoolean("is_anonymous", true) &&
            runCatching { UUID.fromString(user.getString("id")) }.isSuccess
    }

    override suspend fun publishedBooks(token: String): List<CommunityBook> {
        val rows = JSONArray(get(
            "/rest/v1/community_shared_books?select=id,title,author,language" +
                "&status=eq.published&order=published_at.desc,id.asc&limit=40", token
        ))
        require(rows.length() <= 40)
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val title = row.getString("title").trim()
            val author = row.getString("author").trim()
            val language = row.getString("language")
            require(title.length in 1..200 && author.length in 1..200)
            require(language.matches(Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*")))
            CommunityBook(UUID.fromString(row.getString("id")), title, author, language)
        }.also { books -> require(books.map { it.id }.distinct().size == books.size) }
    }

    private suspend fun get(path: String, token: String): String {
        // Defense in depth: even direct client use cannot bypass the release gate.
        check(configuration.isAvailable) { "Community catalog unavailable" }
        require(token.length in 1..8192 && token.none { it.isWhitespace() })
        return suspendCancellableCoroutine { continuation ->
            val request = Request.Builder().url(configuration.url.trimEnd('/') + path)
                .header("apikey", configuration.publishableKey)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json").header("Cache-Control", "no-store").build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        continuation.resume(readResponse(response))
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            })
        }
    }

    private fun readResponse(response: Response): String = response.use {
        if (!response.isSuccessful) throw CommunityHttpException(response.code)
        require(response.header("Content-Type").orEmpty().substringBefore(';').trim() == "application/json")
        val body = response.body ?: throw IOException("Missing response")
        require(body.contentLength() <= MAX_RESPONSE_BYTES)
        body.byteStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_RESPONSE_BYTES)
                output.write(buffer, 0, count)
            }
            output.toString("UTF-8")
        }
    }

    private companion object { const val MAX_RESPONSE_BYTES = 128 * 1024 }
}
