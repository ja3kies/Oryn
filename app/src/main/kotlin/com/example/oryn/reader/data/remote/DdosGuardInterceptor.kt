package com.example.oryn.reader.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

class DdosGuardInterceptor(
    private val bootstrapClient: OkHttpClient,
    private val alwaysBypass: Boolean = false
) : Interceptor {
    private val savedCookies = ConcurrentHashMap<String, Map<String, String>>()
    @Volatile private var ddosBypassPath: String? = null

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        if (alwaysBypass) return@runBlocking bypass(chain, request)
        val response = chain.proceed(request)
        if (response.code == 403) {
            response.close()
            bypass(chain, request)
        } else {
            response
        }
    }

    private fun bypass(chain: Interceptor.Chain, request: Request): Response {
        val host = request.url.host
        val path = ddosBypassPath ?: fetchBypassPath()
        ddosBypassPath = path
        val cookies = savedCookies[host] ?: fetchCookies(request, path).also {
            savedCookies[host] = it
        }
        val newRequest = request.newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .apply {
                if (cookies.isNotEmpty()) {
                    header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            }
            .build()
        return chain.proceed(newRequest)
    }

    private fun fetchBypassPath(): String {
        return bootstrapClient.newCall(
            Request.Builder().url("https://check.ddos-guard.net/check.js").build()
        ).execute().use { response ->
            val body = response.body?.string() ?: return@use ""
            Regex("'(.*?)'").find(body)?.groupValues?.get(1) ?: ""
        }
    }

    private fun fetchCookies(request: Request, path: String): Map<String, String> {
        val url = "${request.url.scheme}://${request.url.host}$path"
        return bootstrapClient.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
        ).execute().use { response ->
            response.headers("Set-Cookie").associate { cookie ->
                val parts = cookie.split(";").first().split("=", limit = 2)
                parts[0].trim() to (parts.getOrNull(1)?.trim() ?: "")
            }
        }
    }
}
