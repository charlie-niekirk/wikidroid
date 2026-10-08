package dev.cniekirk.wikidroid.core.data

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.map
import dev.cniekirk.wikidroid.core.datastore.PreferencesDataSource
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Paged
import dev.cniekirk.wikidroid.core.network.WikiRemoteDataSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    /** Most recent first. */
    val recentSearches: Flow<List<String>>

    /** Title suggestions while typing. A blank [query] returns an empty list without a request. */
    suspend fun autocomplete(query: String): Result<List<ArticleSummary>, DataError>

    /** Full-text results, best match first. Pass `Paged.continuation` back as [continuation] for the next page. */
    suspend fun search(
        query: String,
        continuation: String? = null,
    ): Result<Paged<ArticleSummary>, DataError>

    /** Remembers a submitted search. Not affected by the "save history" preference, which covers viewed pages. */
    suspend fun saveRecentSearch(query: String)

    suspend fun removeRecentSearch(query: String)

    suspend fun clearRecentSearches()
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class WikiSearchRepository(
    private val remote: WikiRemoteDataSource,
    private val preferences: PreferencesDataSource,
) : SearchRepository {
    override val recentSearches: Flow<List<String>> get() = preferences.recentSearches

    override suspend fun autocomplete(query: String): Result<List<ArticleSummary>, DataError> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.Success(emptyList())
        return remote.autocomplete(trimmed).map { pages -> pages.map { it.toSummary() } }
    }

    override suspend fun search(
        query: String,
        continuation: String?,
    ): Result<Paged<ArticleSummary>, DataError> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.Success(Paged(persistentListOf(), continuation = null))
        return remote.searchPages(trimmed, continuation).map { list ->
            Paged(list.pages.map { it.toSummary() }.toImmutableList(), list.continuation)
        }
    }

    override suspend fun saveRecentSearch(query: String) = preferences.addRecentSearch(query)

    override suspend fun removeRecentSearch(query: String) = preferences.removeRecentSearch(query)

    override suspend fun clearRecentSearches() = preferences.clearRecentSearches()
}
