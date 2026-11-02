package com.example.oryn.reader.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class CommunityCatalogViewModel(private var repository: CommunityCatalogRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CommunityCatalogUiState(origin = repository.origin))
    val state = mutableState.asStateFlow()
    private var loading: Job? = null
    private var detailJob: Job? = null
    private var generation = 0L
    private var detailGeneration = 0L
    private var selectedId: UUID? = null

    init { refresh() }

    fun setRepository(value: CommunityCatalogRepository) {
        if (repository === value) return
        repository = value
        closeDetail()
        mutableState.value = mutableState.value.copy(origin = value.origin)
        refresh()
    }

    fun setText(text: String) {
        val value = text.filterNot(Char::isISOControl).take(200)
        if (value != state.value.query.text) firstPage(state.value.query.copy(text = value, page = 1), debounce = true)
    }
    fun setLanguage(language: String?) {
        if (language != state.value.query.language) firstPage(state.value.query.copy(language = language, page = 1))
    }
    fun setSort(sort: CommunitySort) {
        if (sort != state.value.query.sort) firstPage(state.value.query.copy(sort = sort, page = 1))
    }
    fun resetFilters() { firstPage(CommunityQuery()) }
    fun refresh() { firstPage(state.value.query.copy(page = 1)) }

    private fun firstPage(query: CommunityQuery, debounce: Boolean = false) {
        val epoch = ++generation
        loading?.cancel()
        closeDetail()
        val source = repository
        mutableState.value = CommunityCatalogUiState(query, source.origin, CommunityCatalogState.Loading)
        loading = viewModelScope.launch {
            if (debounce) delay(350)
            val result = search(source, query)
            if (generation == epoch) mutableState.value = state.value.copy(result = result)
        }
    }

    fun nextPage() {
        val current = state.value.result as? CommunityCatalogState.Content ?: return
        current.nextPage?.let(::loadPage)
    }
    fun previousPage() {
        if (state.value.query.page > 1) loadPage(state.value.query.page - 1)
    }
    private fun loadPage(page: Int) {
        if (loading?.isActive == true || state.value.loadingMore || (state.value.result !is CommunityCatalogState.Content && state.value.result != CommunityCatalogState.Empty)) return
        val epoch = generation
        val source = repository
        val query = state.value.query.copy(page = page)
        closeDetail()
        mutableState.value = state.value.copy(loadingMore = true, appendFailure = null)
        loading = viewModelScope.launch {
            val next = search(source, query)
            if (generation != epoch) return@launch
            val latest = state.value
            mutableState.value = when (next) {
                is CommunityCatalogState.Content, CommunityCatalogState.Empty -> latest.copy(
                    result = next, query = query, loadingMore = false, appendFailure = null)
                CommunityCatalogState.SignInRequired, CommunityCatalogState.Unavailable -> latest.copy(result = next, loadingMore = false)
                else -> latest.copy(loadingMore = false, appendFailure = next)
            }
        }
    }

    fun openBook(id: UUID) {
        val epoch = ++detailGeneration
        detailJob?.cancel()
        selectedId = id
        val source = repository
        mutableState.value = state.value.copy(detail = CommunityDetailState.Loading)
        detailJob = viewModelScope.launch {
            val result = try {
                val value = source.details(id)
                if (value is CommunityDetailState.Content &&
                    (value.details.book.id != id || value.details.book.origin != source.origin)) CommunityDetailState.Error else value
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { CommunityDetailState.Error }
            if (detailGeneration == epoch) mutableState.value = state.value.copy(detail = result)
        }
    }
    fun retryDetails() { selectedId?.let(::openBook) }
    fun closeDetail() {
        detailGeneration++
        detailJob?.cancel()
        selectedId = null
        mutableState.value = state.value.copy(detail = CommunityDetailState.Closed)
    }

    private suspend fun search(source: CommunityCatalogRepository, query: CommunityQuery): CommunityCatalogState = try {
        val value = source.search(query)
        if (value is CommunityCatalogState.Content) {
            require(value.books.size <= 40 && value.books.all { it.origin == source.origin })
            require(value.nextPage == null || (value.nextPage == query.page + 1 && query.page < 1000))
        }
        value
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) { CommunityCatalogState.Error }
}
