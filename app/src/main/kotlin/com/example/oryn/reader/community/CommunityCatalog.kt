package com.example.oryn.reader.community

import com.example.oryn.reader.BuildConfig
import java.net.URI
import java.util.UUID

/** Remote metadata only. Never contains a local path, owner, or Storage URL. */
enum class CommunityOrigin { Remote, Simulation }

data class CommunityBook(
    val id: UUID, val title: String, val author: String, val language: String,
    val origin: CommunityOrigin = CommunityOrigin.Remote
) {
    init {
        require(title.isNotBlank() && title.length <= 200 && title.none(Char::isISOControl))
        require(author.isNotBlank() && author.length <= 200 && author.none(Char::isISOControl))
        require(language.length <= 35 && language.matches(Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*")))
    }
}

sealed interface CommunityCatalogState {
    data object Unavailable : CommunityCatalogState
    data object SignInRequired : CommunityCatalogState
    data object Loading : CommunityCatalogState
    data object Empty : CommunityCatalogState
    data object Offline : CommunityCatalogState
    data object Error : CommunityCatalogState
    data class Content(val books: List<CommunityBook>, val nextPage: Int? = null) : CommunityCatalogState
}

class CommunityConfiguration(val url: String, val publishableKey: String) {
    // Deliberate release gate: configuration alone must never enable production traffic.
    val isAvailable: Boolean get() = PRODUCTION_VERIFIED && isValid
    internal val isValid: Boolean get() = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            uri.port == -1 && uri.rawQuery == null && uri.rawFragment == null &&
            uri.path.orEmpty() in listOf("", "/") &&
            publishableKey.matches(Regex("sb_publishable_[A-Za-z0-9_-]{10,200}"))
    }.getOrDefault(false)

    companion object {
        private const val PRODUCTION_VERIFIED = false
        fun fromBuild() = CommunityConfiguration(
            BuildConfig.COMMUNITY_SUPABASE_URL, BuildConfig.COMMUNITY_SUPABASE_PUBLISHABLE_KEY
        )
    }
}

/** Session contract shared with the optional, isolated Supabase Auth/PKCE controller.
 * Implementations must return a current user access token, never a project API key.
 * Tokens are revalidated by Supabase Auth; local JWT claims do not authorize access.
 */
interface CommunitySessionProvider {
    suspend fun accessToken(): String?
    suspend fun clear()
    /** Atomic, non-network check; unknown providers fail closed for late responses. */
    suspend fun isCurrent(accessToken: String): Boolean = false
    /** Rotating providers override this atomically so an old 401 cannot erase a newer session. */
    suspend fun invalidate(accessToken: String) { clear() }
}

object SignedOutCommunitySession : CommunitySessionProvider {
    override suspend fun accessToken(): String? = null
    override suspend fun clear() = Unit
}

interface CommunityCatalogApi {
    suspend fun verifyUser(token: String): Boolean
    suspend fun publishedBooks(token: String): List<CommunityBook>
}

interface CommunityCatalogRepository {
    val origin: CommunityOrigin get() = CommunityOrigin.Remote
    suspend fun load(): CommunityCatalogState = search(CommunityQuery())
    suspend fun search(query: CommunityQuery): CommunityCatalogState
    suspend fun details(id: UUID): CommunityDetailState
}
