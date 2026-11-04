package com.example.oryn.reader.data.remote

import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class WebDownloadHeadersTest {
    private val initial = URL("https://books.example/download?id=1")
    private val headers = mapOf(
        "Cookie" to "session=private", "Authorization" to "Bearer private", "X-Api-Key" to "private",
        "Referer" to "https://books.example/?private=1", "User-Agent" to "Oryn", "Accept" to "application/epub+zip"
    )

    @Test fun sameOriginRetainsRequiredBrowserCredentials() {
        assertEquals(headers, WebDownloadHeaders.forOrigin(initial, URL("https://BOOKS.example:443/new"), headers))
    }

    @Test fun redirectToAnotherHostOrPortNeverGetsCredentials() {
        for (target in listOf("https://cdn.example/book", "https://books.example:8443/book", "http://books.example/book")) {
            assertEquals(mapOf("User-Agent" to "Oryn", "Accept" to "application/epub+zip"),
                WebDownloadHeaders.forOrigin(initial, URL(target), headers))
        }
    }

    @Test fun subdomainAndSuffixAreDifferentOrigins() {
        for (target in listOf("https://cdn.books.example/book", "https://books.example.attacker.test/book")) {
            assertNull(WebDownloadHeaders.forOrigin(initial, URL(target), headers)["Cookie"])
        }
    }

    @Test fun invalidHeaderNamesAndLineBreaksAreRejectedOnEveryOrigin() {
        val invalid = mapOf("Cookie" to "session=private\r\nInjected: bad", "Bad Name" to "bad", "Accept" to "a".repeat(4097))
        assertTrue(WebDownloadHeaders.forOrigin(initial, initial, invalid).isEmpty())
    }
}
