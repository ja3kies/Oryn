package com.example.oryn.reader.data.remote

import java.net.URL

/** Browser credentials belong to the initiating origin, never a redirected download host. */
internal object WebDownloadHeaders {
    private val publicHeaders = setOf("user-agent", "accept", "accept-language")

    fun forOrigin(initial: URL, target: URL, headers: Map<String, String>): Map<String, String> {
        val sameOrigin = initial.protocol.equals(target.protocol, true) &&
            initial.host.equals(target.host, true) && port(initial) == port(target)
        return headers.filter { (name, value) ->
            name.matches(Regex("[A-Za-z0-9-]{1,64}")) && value.length <= 4096 &&
                value.none { it == '\r' || it == '\n' } &&
                (sameOrigin || name.lowercase(java.util.Locale.ROOT) in publicHeaders)
        }
    }

    private fun port(url: URL) = if (url.port == -1) url.defaultPort else url.port
}
