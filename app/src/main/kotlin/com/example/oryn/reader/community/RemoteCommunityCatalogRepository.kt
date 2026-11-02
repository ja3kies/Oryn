package com.example.oryn.reader.community

import kotlinx.coroutines.CancellationException
import org.json.JSONException
import java.io.IOException

/** No link to Room or the import pipeline. This read-only phase cannot upload an EPUB. */
class RemoteCommunityCatalogRepository(
    private val configuration: CommunityConfiguration,
    private val session: CommunitySessionProvider,
    private val api: CommunityCatalogApi
) : CommunityCatalogRepository {
    override suspend fun search(query: CommunityQuery): CommunityCatalogState {
        // Non-default queries need a verified server contract; never fabricate remote pagination.
        if (query != CommunityQuery()) return CommunityCatalogState.Unavailable
        // Check before session lookup or any network operation.
        if (!configuration.isAvailable) return CommunityCatalogState.Unavailable
        var attemptedToken: String? = null
        return try {
            val token = session.accessToken() ?: return CommunityCatalogState.SignInRequired
            attemptedToken = token
            if (!api.verifyUser(token)) {
                session.invalidate(token)
                return CommunityCatalogState.SignInRequired
            }
            val books = api.publishedBooks(token)
            if (!session.isCurrent(token)) return CommunityCatalogState.SignInRequired
            if (books.isEmpty()) CommunityCatalogState.Empty else CommunityCatalogState.Content(books)
        } catch (error: CancellationException) {
            throw error
        } catch (error: CommunityHttpException) {
            when (error.status) {
                401 -> { attemptedToken?.let { session.invalidate(it) }; CommunityCatalogState.SignInRequired }
                404, 503 -> CommunityCatalogState.Unavailable
                else -> CommunityCatalogState.Error
            }
        } catch (_: JSONException) {
            CommunityCatalogState.Error
        } catch (_: IllegalArgumentException) {
            CommunityCatalogState.Error
        } catch (_: IOException) {
            CommunityCatalogState.Offline
        } catch (_: Exception) {
            CommunityCatalogState.Error
        }
    }
    override suspend fun details(id: java.util.UUID): CommunityDetailState {
        // Details must not fall back to simulated metadata or bypass the network release gate.
        return CommunityDetailState.Unavailable
    }

}
