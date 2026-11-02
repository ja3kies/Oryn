package com.example.oryn.reader.community.auth

import java.util.UUID

// Unit-test-only fixtures. No fallback URL, provider, key or credential exists in the application.
internal fun enabledConfig() = AuthConfiguration("https://auth.example.test", "sb_publishable_unit_fixture_12345", "reader.example.test", "github", true, true, true)
internal val testUser: UUID = UUID.fromString("10000000-0000-4000-8000-000000000001")
internal fun testTokens(tag: String = "new", ttl: Long = 3600) = AuthTokens("test-access-$tag", "test-refresh-$tag", ttl)
internal class MemoryAuthStore : AuthStore {
    var value: StoredAuth? = null
    var reads = 0
    var writes = 0
    override suspend fun read(): StoredAuth? { reads++; return value }
    override suspend fun write(value: StoredAuth) { writes++; this.value = value }
    override suspend fun clear() { value = null }
}
internal open class FakeAuthApi : AuthApi {
    var exchanges = 0
    var refreshes = 0
    var verifications = 0
    var logouts = 0
    var failure: Exception? = null
    override suspend fun exchange(code: String, verifier: String): AuthTokens {
        exchanges++; failure?.let { throw it }; return testTokens()
    }
    override suspend fun refresh(refreshToken: String): AuthTokens {
        refreshes++; failure?.let { throw it }; return testTokens("rotated")
    }
    override suspend fun userId(accessToken: String): UUID {
        verifications++; failure?.let { throw it }; return testUser
    }
    override suspend fun logout(accessToken: String) { logouts++; failure?.let { throw it } }
}
