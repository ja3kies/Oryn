package com.example.oryn.reader.community

import java.text.Normalizer
import java.util.Locale
import java.util.UUID

enum class CommunitySort { TitleAscending, TitleDescending }

data class CommunityQuery(
    val text: String = "", val language: String? = null,
    val sort: CommunitySort = CommunitySort.TitleAscending,
    val page: Int = 1, val pageSize: Int = 6
) {
    init {
        require(text.length <= 200 && text.none(Char::isISOControl))
        require(language == null || (language.length <= 35 && language.matches(Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*"))))
        require(page in 1..1000 && pageSize in 1..40)
    }
}

data class CommunityPage(val books: List<CommunityBook>, val nextPage: Int?)

data class CommunityBookDetails(
    val book: CommunityBook, val description: String? = null,
    val sizeBytes: Long? = null, val sha256: String? = null
) {
    init {
        require(description == null || description.length <= 4000)
        require(sizeBytes == null || sizeBytes in 1..MAX_COMMUNITY_EPUB_BYTES)
        require(sha256 == null || sha256.matches(Regex("[a-f0-9]{64}")))
    }
}

const val MAX_COMMUNITY_EPUB_BYTES = 8L * 1024 * 1024

sealed interface CommunityDetailState {
    data object Closed : CommunityDetailState
    data object Loading : CommunityDetailState
    data object Unavailable : CommunityDetailState
    data object SignInRequired : CommunityDetailState
    data object Offline : CommunityDetailState
    data object Error : CommunityDetailState
    data object NotFound : CommunityDetailState
    data class Content(val details: CommunityBookDetails) : CommunityDetailState
}

data class CommunityCatalogUiState(
    val query: CommunityQuery = CommunityQuery(),
    val origin: CommunityOrigin = CommunityOrigin.Remote,
    val result: CommunityCatalogState = CommunityCatalogState.Unavailable,
    val loadingMore: Boolean = false,
    val appendFailure: CommunityCatalogState? = null,
    val detail: CommunityDetailState = CommunityDetailState.Closed
)

/** Injectable metadata source. No file handle, session, URL or upload operation in this contract. */
interface CommunityCatalogDataSource {
    suspend fun search(query: CommunityQuery): CommunityPage
    suspend fun details(id: UUID): CommunityBookDetails?
}

class CommunitySourceFailure(val state: CommunityCatalogState) : Exception("Community source unavailable")

enum class CommunitySimulationScenario { Content, Loading, Empty, Error, Offline, SignInRequired, Unavailable }

/** Shared deterministic search/paging rules; punctuation is literal, never a SQL/regex expression. */
internal fun communityPage(
    books: List<CommunityBook>, query: CommunityQuery,
    searchAliases: Map<UUID, List<String>> = emptyMap()
): CommunityPage {
    fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
    val text = normalized(query.text)
    val matches = books.filter {
        (query.language == null || it.language == query.language) &&
            (text.isEmpty() || normalized(it.title).contains(text) || normalized(it.author).contains(text) ||
                searchAliases[it.id].orEmpty().any { alias -> normalized(alias).contains(text) })
    }
    val comparator = compareBy<CommunityBook> { normalized(it.title) }.thenBy { it.id.toString() }
    val sorted = matches.sortedWith(if (query.sort == CommunitySort.TitleAscending) comparator else comparator.reversed())
    val start = (query.page - 1) * query.pageSize
    val slice = sorted.drop(start).take(query.pageSize)
    return CommunityPage(slice, if (start + slice.size < sorted.size && query.page < 1000) query.page + 1 else null)
}
