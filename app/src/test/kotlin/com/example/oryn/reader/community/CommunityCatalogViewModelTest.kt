package com.example.oryn.reader.community

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CommunityCatalogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var count = 0
    private val books = (1..18).map { index -> CommunityBook(UUID(0, index.toLong()), "Test " + index.toString().padStart(2, '0'),
        "Test author", if (index % 2 == 0) "en" else "fr", CommunityOrigin.Simulation) }

    private open inner class Repository : CommunityCatalogRepository {
        override val origin = CommunityOrigin.Simulation
        override suspend fun search(query: CommunityQuery): CommunityCatalogState {
            val page = communityPage(books, query)
            return if (page.books.isEmpty()) CommunityCatalogState.Empty else CommunityCatalogState.Content(page.books, page.nextPage)
        }
        override suspend fun details(id: UUID): CommunityDetailState = books.find { it.id == id }?.let {
            CommunityDetailState.Content(CommunityBookDetails(it))
        } ?: CommunityDetailState.NotFound
    }
    private fun model(repo: CommunityCatalogRepository = Repository()) = CommunityCatalogViewModel(repo).also { store.put((count++).toString(), it) }
    @Before fun main() { Dispatchers.setMain(dispatcher) }
    @After fun finish() { store.clear(); Dispatchers.resetMain() }

    @Test fun filterAndSearchResetPaginationAndKeepOnlyOneBoundedPage() = runTest(dispatcher) {
        val model = model(); advanceUntilIdle()
        model.nextPage(); advanceUntilIdle()
        assertEquals(2, model.state.value.query.page)
        assertEquals(6, (model.state.value.result as CommunityCatalogState.Content).books.size)
        model.previousPage(); advanceUntilIdle()
        assertEquals(1, model.state.value.query.page)
        model.nextPage(); advanceUntilIdle()
        model.setLanguage("fr"); advanceUntilIdle()
        assertEquals(1, model.state.value.query.page)
        assertTrue((model.state.value.result as CommunityCatalogState.Content).books.all { it.language == "fr" })
        model.setText("Test 17"); advanceUntilIdle()
        assertEquals(listOf(books[16]), (model.state.value.result as CommunityCatalogState.Content).books)
        model.resetFilters(); advanceUntilIdle()
        assertEquals(CommunityQuery(), model.state.value.query)
        model.nextPage(); advanceUntilIdle()
        model.setSort(CommunitySort.TitleDescending); advanceUntilIdle()
        assertEquals(1, model.state.value.query.page)
        assertEquals(books.last(), (model.state.value.result as CommunityCatalogState.Content).books.first())
    }

    @Test fun debounceAndGenerationPreventOldSearchOverwritingNewSearch() = runTest(dispatcher) {
        val repo = object : Repository() {
            override suspend fun search(query: CommunityQuery): CommunityCatalogState {
                if (query.text == "old") withContext(NonCancellable) { delay(1000) }
                return CommunityCatalogState.Content(listOf(if (query.text == "old") books[0] else books[1]))
            }
        }
        val model = model(repo); advanceUntilIdle()
        model.setText("old"); advanceTimeBy(350); runCurrent()
        model.setText("new"); advanceTimeBy(350); runCurrent()
        assertEquals(books[1], (model.state.value.result as CommunityCatalogState.Content).books.single())
        advanceUntilIdle()
        assertEquals("new", model.state.value.query.text)
        assertEquals(books[1], (model.state.value.result as CommunityCatalogState.Content).books.single())
    }

    @Test fun paginationErrorsPreserveCurrentPageAndCanBeRetried() = runTest(dispatcher) {
        var fail = true
        val repo = object : Repository() {
            override suspend fun search(query: CommunityQuery): CommunityCatalogState =
                if (query.page == 2 && fail) CommunityCatalogState.Offline else super.search(query)
        }
        val model = model(repo); advanceUntilIdle()
        val first = model.state.value.result
        model.nextPage(); model.nextPage(); advanceUntilIdle()
        assertEquals(first, model.state.value.result)
        assertEquals(1, model.state.value.query.page)
        assertEquals(CommunityCatalogState.Offline, model.state.value.appendFailure)
        fail = false; model.nextPage(); advanceUntilIdle()
        assertEquals(2, model.state.value.query.page)
        assertNull(model.state.value.appendFailure)
    }

    @Test fun expiredAuthenticationDuringPaginationClearsOldResults() = runTest(dispatcher) {
        val repo = object : Repository() {
            override suspend fun search(query: CommunityQuery) = if (query.page == 2) CommunityCatalogState.SignInRequired else super.search(query)
        }
        val model = model(repo); advanceUntilIdle(); model.nextPage(); advanceUntilIdle()
        assertEquals(CommunityCatalogState.SignInRequired, model.state.value.result)
    }

    @Test fun emptyPageStillAllowsGoingBack() = runTest(dispatcher) {
        val repo = object : Repository() {
            override suspend fun search(query: CommunityQuery) = if (query.page == 2) CommunityCatalogState.Empty else super.search(query)
        }
        val model = model(repo); advanceUntilIdle(); model.nextPage(); advanceUntilIdle()
        assertEquals(CommunityCatalogState.Empty, model.state.value.result)
        model.previousPage(); advanceUntilIdle()
        assertTrue(model.state.value.result is CommunityCatalogState.Content)
        assertEquals(1, model.state.value.query.page)
    }

    @Test fun staleDetailsCannotReopenAfterDismissalOrNewSelection() = runTest(dispatcher) {
        val repo = object : Repository() {
            override suspend fun details(id: UUID): CommunityDetailState {
                if (id == books[0].id) withContext(NonCancellable) { delay(1000) }
                return super.details(id)
            }
        }
        val model = model(repo); advanceUntilIdle()
        model.openBook(books[0].id); runCurrent(); model.closeDetail(); advanceUntilIdle()
        assertEquals(CommunityDetailState.Closed, model.state.value.detail)
        model.openBook(books[0].id); runCurrent(); model.openBook(books[1].id); advanceUntilIdle()
        assertEquals(books[1], (model.state.value.detail as CommunityDetailState.Content).details.book)
    }

    @Test fun switchingToLockedRemoteCannotRetainSimulatedResultsOrDetails() = runTest(dispatcher) {
        val model = model(); advanceUntilIdle(); model.openBook(books[0].id); advanceUntilIdle()
        val remote = object : CommunityCatalogRepository {
            override suspend fun search(query: CommunityQuery) = CommunityCatalogState.Unavailable
            override suspend fun details(id: UUID) = CommunityDetailState.Unavailable
        }
        model.setRepository(remote); advanceUntilIdle()
        assertEquals(CommunityOrigin.Remote, model.state.value.origin)
        assertEquals(CommunityCatalogState.Unavailable, model.state.value.result)
        assertEquals(CommunityDetailState.Closed, model.state.value.detail)
    }
}
