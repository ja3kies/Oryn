package com.example.oryn.reader.community

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommunityCatalogTest {
    private val config = CommunityConfiguration("https://example.supabase.co", "sb_publishable_example1234567890")

    @Test fun releaseGatePreventsSessionAndNetworkAccess() = runBlocking {
        val session = object : CommunitySessionProvider {
            override suspend fun accessToken(): String? = error("Session must not be accessed")
            override suspend fun clear() = error("Session must not be cleared")
        }
        val api = object : CommunityCatalogApi {
            override suspend fun verifyUser(token: String): Boolean = error("No Auth request allowed")
            override suspend fun publishedBooks(token: String): List<CommunityBook> = error("No catalog request allowed")
        }
        assertTrue(config.isValid)
        assertFalse(config.isAvailable)
        assertEquals(CommunityCatalogState.Unavailable, RemoteCommunityCatalogRepository(config, session, api).load())
    }

    @Test fun directApiCallsCannotBypassReleaseGate() = runBlocking {
        val api = SupabaseCommunityCatalogApi(config)
        for (call in listOf<suspend () -> Any>({ api.verifyUser("unused") }, { api.publishedBooks("unused") })) {
            try {
                call()
                fail("Client must reject calls while the backend is unverified")
            } catch (_: IllegalStateException) {
                // Guard runs before client construction or I/O.
            }
        }
    }

    @Test fun rejectsPrivilegedKeysAndUnsafeEndpoints() {
        assertFalse(CommunityConfiguration(config.url, "sb_secret_example1234567890").isValid)
        assertFalse(CommunityConfiguration(config.url, "eyJhbGciOiJIUzI1NiJ9.service_role.signature").isValid)
        listOf("http://example.com", "https://user@example.com", "https://example.com/path",
            "https://example.com?key=value", "https://example.com#fragment", "https://example.com:444").forEach {
            assertFalse(it, CommunityConfiguration(it, config.publishableKey).isValid)
        }
    }
    @Test fun allNewRemoteEntryPointsRemainLockedBeforeAuthAndNetwork() = runBlocking {
        val session = object : CommunitySessionProvider {
            override suspend fun accessToken(): String? = error("Auth must not be read")
            override suspend fun clear() = error("Auth must not be changed")
        }
        val api = object : CommunityCatalogApi {
            override suspend fun verifyUser(token: String): Boolean = error("No network")
            override suspend fun publishedBooks(token: String): List<CommunityBook> = error("No network")
        }
        val remote = RemoteCommunityCatalogRepository(config, session, api)
        for (query in listOf(CommunityQuery(), CommunityQuery("search", "fr"), CommunityQuery(page = 2))) {
            assertEquals(CommunityCatalogState.Unavailable, remote.search(query))
        }
        assertEquals(CommunityDetailState.Unavailable, remote.details(java.util.UUID.randomUUID()))
    }

}
