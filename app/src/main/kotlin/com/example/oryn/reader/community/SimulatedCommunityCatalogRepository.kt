package com.example.oryn.reader.community

import com.example.oryn.reader.BuildConfig
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.UUID

/** Read-only simulation, never an authenticated remote repository. Fixtures live in src/debug. */
class SimulatedCommunityCatalogRepository(private val source: CommunityCatalogDataSource) : CommunityCatalogRepository {
    init { check(BuildConfig.DEBUG) { "Community simulation is development-only" } }
    override val origin = CommunityOrigin.Simulation

    override suspend fun search(query: CommunityQuery): CommunityCatalogState = try {
        val page = source.search(query)
        require(page.books.size <= query.pageSize)
        require(page.books.all { it.origin == CommunityOrigin.Simulation })
        require(page.books.map { it.id }.distinct().size == page.books.size)
        require(page.nextPage == null || (page.nextPage == query.page + 1 && page.books.isNotEmpty() && query.page < 1000))
        if (page.books.isEmpty()) CommunityCatalogState.Empty else CommunityCatalogState.Content(page.books, page.nextPage)
    } catch (error: CancellationException) { throw error }
    catch (error: CommunitySourceFailure) {
        when (error.state) {
            CommunityCatalogState.Error, CommunityCatalogState.Offline,
            CommunityCatalogState.SignInRequired, CommunityCatalogState.Unavailable -> error.state
            else -> CommunityCatalogState.Error
        }
    } catch (_: IOException) { CommunityCatalogState.Offline }
    catch (_: Exception) { CommunityCatalogState.Error }

    override suspend fun details(id: UUID): CommunityDetailState = try {
        val details = source.details(id)
        if (details == null) CommunityDetailState.NotFound else {
            require(details.book.id == id && details.book.origin == CommunityOrigin.Simulation)
            CommunityDetailState.Content(details)
        }
    } catch (error: CancellationException) { throw error }
    catch (error: CommunitySourceFailure) {
        when (error.state) {
            CommunityCatalogState.Offline -> CommunityDetailState.Offline
            CommunityCatalogState.SignInRequired -> CommunityDetailState.SignInRequired
            CommunityCatalogState.Unavailable -> CommunityDetailState.Unavailable
            else -> CommunityDetailState.Error
        }
    } catch (_: IOException) { CommunityDetailState.Offline }
    catch (_: Exception) { CommunityDetailState.Error }
}
