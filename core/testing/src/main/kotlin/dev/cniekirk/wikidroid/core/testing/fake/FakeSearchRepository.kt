package dev.cniekirk.wikidroid.core.testing.fake

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.data.SearchRepository
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Paged
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Answers from [autocompleteHandler] and [searchHandler], which tests replace to script results.
 * Recent searches behave like the real store: case-insensitive de-duplication, newest first, capped.
 */
class FakeSearchRepository : SearchRepository {
    var autocompleteHandler: suspend (query: String) -> Result<List<ArticleSummary>, DataError> =
        { Result.Success(emptyList()) }
    var searchHandler: suspend (query: String, continuation: String?) -> Result<Paged<ArticleSummary>, DataError> =
        { _, _ -> Result.Success(Paged(persistentListOf(), continuation = null)) }

    val autocompleteQueries = mutableListOf<String>()
    val searchRequests = mutableListOf<Pair<String, String?>>()

    private val recent = MutableStateFlow<List<String>>(emptyList())
    override val recentSearches: Flow<List<String>> get() = recent

    override suspend fun autocomplete(query: String): Result<List<ArticleSummary>, DataError> {
        autocompleteQueries += query
        return autocompleteHandler(query)
    }

    override suspend fun search(
        query: String,
        continuation: String?,
    ): Result<Paged<ArticleSummary>, DataError> {
        searchRequests += query to continuation
        return searchHandler(query, continuation)
    }

    override suspend fun saveRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        recent.update { list ->
            (listOf(trimmed) + list.filterNot { it.equals(trimmed, ignoreCase = true) }).take(MAX_RECENT)
        }
    }

    override suspend fun removeRecentSearch(query: String) {
        recent.update { list -> list.filterNot { it.equals(query.trim(), ignoreCase = true) } }
    }

    override suspend fun clearRecentSearches() {
        recent.value = emptyList()
    }

    private companion object {
        const val MAX_RECENT = 10
    }
}
