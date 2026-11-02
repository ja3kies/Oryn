package com.example.oryn.reader.community.auth

import com.example.oryn.reader.BuildConfig
import com.example.oryn.reader.community.CommunityConfiguration
import java.security.MessageDigest
import java.util.UUID

/** Auth is debug-only and requires an explicitly verified isolated configuration.
 * The catalogue has its own unconditional release gate, unaffected by these flags.
 */
class AuthConfiguration(
    val origin: String,
    val publishableKey: String,
    val redirectHost: String,
    val provider: String,
    private val enabled: Boolean,
    private val debug: Boolean,
    private val isolatedVerified: Boolean
) {
    val isEnabled: Boolean get() = enabled && debug && isolatedVerified &&
        CommunityConfiguration(origin, publishableKey).isValid &&
        redirectHost.matches(Regex("[a-z0-9]+(?:[.-][a-z0-9]+)*\\.[a-z]{2,63}")) &&
        !redirectHost.endsWith(".supabase.co") &&
        provider in setOf("github", "google", "azure", "apple", "gitlab", "keycloak")
    val redirectUri: String get() = "https://$redirectHost$CALLBACK_PATH"
    internal val binding: String get() = MessageDigest.getInstance("SHA-256")
        .digest("$origin|$publishableKey|$redirectUri|$provider".toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val CALLBACK_PATH = "/auth/callback"
        fun fromBuild() = AuthConfiguration(
            BuildConfig.COMMUNITY_SUPABASE_URL, BuildConfig.COMMUNITY_SUPABASE_PUBLISHABLE_KEY,
            BuildConfig.COMMUNITY_AUTH_REDIRECT_HOST, BuildConfig.COMMUNITY_AUTH_PROVIDER,
            BuildConfig.COMMUNITY_AUTH_ENABLED, BuildConfig.DEBUG, BuildConfig.COMMUNITY_AUTH_ISOLATED_VERIFIED
        )
    }
}

enum class AuthStatus {
    Disabled, SignedOut, Busy, AwaitingCallback, SignedIn, Offline, InvalidSession,
    InvalidCallback, Cancelled, Expired, Error, StorageError, RedirectUnavailable, SignedOutRevocationUnconfirmed
}

data class AuthUiState(val status: AuthStatus, val hasSession: Boolean = false, val hasPending: Boolean = false)

/** Deliberately not a data class: generated toString/copy/component functions must not expose credentials. */
class AuthTokens(val access: String, val refresh: String, val expiresIn: Long) {
    init {
        require(access.length in 1..8192 && access.all { it.code in 33..126 })
        require(refresh.length in 1..8192 && refresh.all { it.code in 33..126 })
        require(expiresIn in 1..86400)
    }
    override fun toString() = "AuthTokens([redacted])"
}

class AuthSession(val tokens: AuthTokens, val userId: UUID, val issuedAt: Long, val expiresAt: Long) {
    fun needsRefresh(now: Long) = now >= expiresAt - 60 || now < issuedAt - 60
    override fun toString() = "AuthSession([redacted])"
}

class PkceAttempt(val verifier: String, val nonce: String, val createdAt: Long) {
    fun isFresh(now: Long) = now >= createdAt && now - createdAt < 300
    override fun toString() = "PkceAttempt([redacted])"
}

class StoredAuth(val binding: String, val session: AuthSession? = null, val pending: PkceAttempt? = null) {
    override fun toString() = "StoredAuth([redacted])"
}

interface AuthStore {
    suspend fun read(): StoredAuth?
    suspend fun write(value: StoredAuth)
    suspend fun clear()
}

interface AuthApi {
    suspend fun exchange(code: String, verifier: String): AuthTokens
    suspend fun refresh(refreshToken: String): AuthTokens
    suspend fun userId(accessToken: String): UUID
    suspend fun logout(accessToken: String)
}

class AuthHttpException(val status: Int) : Exception("Auth request rejected")
class InvalidAuthSession : Exception("Invalid auth session")
class AuthStorageException : Exception("Secure auth storage unavailable")
