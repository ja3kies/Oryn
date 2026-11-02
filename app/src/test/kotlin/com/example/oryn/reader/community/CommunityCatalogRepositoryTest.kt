package com.example.oryn.reader.community

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CommunityCatalogRepositoryTest {
    private fun repository(scenario: CommunitySimulationScenario = CommunitySimulationScenario.Content) =
        SimulatedCommunityCatalogRepository(DevelopmentCommunitySource(scenario, latencyMs = 0))

    @Test fun combinesAccentInsensitiveSearchLanguageAndStablePagination() = runBlocking {
        val repo = repository()
        val ids = mutableListOf<UUID>()
        for (page in 1..3) {
            val result = repo.search(CommunityQuery("  DÉMONSTRATION  ", "fr", page = page, pageSize = 4)) as CommunityCatalogState.Content
            assertTrue(result.books.all { it.language == "fr" && it.origin == CommunityOrigin.Simulation })
            ids += result.books.map { it.id }
            assertEquals(if (page < 3) page + 1 else null, result.nextPage)
        }
        assertEquals(9, ids.size)
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(CommunityCatalogState.Empty, repo.search(CommunityQuery("missing-result")))
        assertEquals(CommunityCatalogState.Empty, repo.search(CommunityQuery(".*"))) // Literal input, not regex.
    }

    @Test fun sortsBothWaysAndNeverInventsAnotherPageAtTheEnd() = runBlocking {
        val repo = repository()
        val ascending = repo.search(CommunityQuery(pageSize = 40)) as CommunityCatalogState.Content
        val descending = repo.search(CommunityQuery(sort = CommunitySort.TitleDescending, pageSize = 40)) as CommunityCatalogState.Content
        assertEquals(ascending.books.reversed(), descending.books)
        assertEquals(18, ascending.books.size)
        assertNull(ascending.nextPage)
        assertEquals(CommunityCatalogState.Empty, repo.search(CommunityQuery(page = 1000)))
    }

    @Test fun mapsEveryDevelopmentScenarioWithoutAnyAuthSession() = runBlocking {
        val scenarios = mapOf(
            CommunitySimulationScenario.Empty to CommunityCatalogState.Empty,
            CommunitySimulationScenario.Error to CommunityCatalogState.Error,
            CommunitySimulationScenario.Offline to CommunityCatalogState.Offline,
            CommunitySimulationScenario.SignInRequired to CommunityCatalogState.SignInRequired,
            CommunitySimulationScenario.Unavailable to CommunityCatalogState.Unavailable
        )
        scenarios.forEach { (scenario, expected) -> assertEquals(expected, repository(scenario).load()) }
        assertEquals(CommunityDetailState.Offline, repository(CommunitySimulationScenario.Offline).details(UUID.randomUUID()))
        assertEquals(CommunityDetailState.SignInRequired, repository(CommunitySimulationScenario.SignInRequired).details(UUID.randomUUID()))
    }

    @Test fun detailsAreBoundToIdAndContainNoRealFileOrPublication() = runBlocking {
        val repo = repository()
        val book = (repo.load() as CommunityCatalogState.Content).books.first()
        val details = (repo.details(book.id) as CommunityDetailState.Content).details
        assertEquals(book, details.book)
        assertEquals(CommunityOrigin.Simulation, details.book.origin)
        assertNull(details.sizeBytes); assertNull(details.sha256)
        assertEquals(CommunityDetailState.NotFound, repo.details(UUID.randomUUID()))
    }

    @Test fun rejectsMixedProvenanceDuplicateIdsAndInvalidPageCursors() = runBlocking {
        val simulated = (repository().load() as CommunityCatalogState.Content).books.first()
        val remote = simulated.copy(origin = CommunityOrigin.Remote)
        for (page in listOf(CommunityPage(listOf(remote), null), CommunityPage(listOf(simulated, simulated), null),
            CommunityPage(listOf(simulated), 1), CommunityPage(emptyList(), 2))) {
            val source = object : CommunityCatalogDataSource {
                override suspend fun search(query: CommunityQuery) = page
                override suspend fun details(id: UUID) = null
            }
            assertEquals(CommunityCatalogState.Error, SimulatedCommunityCatalogRepository(source).load())
        }
    }

    @Test fun rejectsDetailsForAnotherIdAndBoundsUntrustedMetadata() = runBlocking {
        val book = (repository().load() as CommunityCatalogState.Content).books.first()
        val source = object : CommunityCatalogDataSource {
            override suspend fun search(query: CommunityQuery) = CommunityPage(listOf(book), null)
            override suspend fun details(id: UUID) = CommunityBookDetails(book)
        }
        assertEquals(CommunityDetailState.Error, SimulatedCommunityCatalogRepository(source).details(UUID.randomUUID()))
        expectInvalid { CommunityBook(UUID.randomUUID(), "title\nspoof", "author", "fr") }
        expectInvalid { CommunityBookDetails(book, description = "x".repeat(4001)) }
        expectInvalid { CommunityBookDetails(book, sizeBytes = MAX_COMMUNITY_EPUB_BYTES + 1) }
        expectInvalid { CommunityBookDetails(book, sha256 = "not-a-sha256") }
        expectInvalid { CommunityQuery(text = "x".repeat(201)) }
        expectInvalid { CommunityQuery(page = 0) }
        expectInvalid { CommunityQuery(pageSize = 41) }
    }

    @Test fun loadingScenarioIsCancellableWithoutBecomingAnError() = runTest {
        val request = async { repository(CommunitySimulationScenario.Loading).load() }
        runCurrent()
        assertTrue(request.isActive)
        request.cancelAndJoin()
        assertTrue(request.isCancelled)
    }

    private fun expectInvalid(block: () -> Any) {
        try { block(); fail("Invalid metadata accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun searchesTheVisibleEnglishDemonstrationLabelsAsWellAsFrenchMetadata() = runBlocking {
        val repo = repository()
        val title = repo.search(CommunityQuery("Demonstration EPUB 01")) as CommunityCatalogState.Content
        assertEquals(1, title.books.size)
        assertTrue(title.books.single().title.endsWith("01"))
        val author = repo.search(CommunityQuery("Demonstration author", pageSize = 40)) as CommunityCatalogState.Content
        assertEquals(18, author.books.size)
    }

}
