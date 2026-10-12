package dev.cniekirk.wikidroid.feature.search

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.fold
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Paged
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The Search tab has three modes, picked from this state: recent searches while [query] is blank, title
 * [suggestions] while it is being typed, and full-text [results] once it was submitted.
 */
@Immutable
data class SearchState(
    val query: String = "",
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val suggestions: SuggestionsState = SuggestionsState.Idle,
    val results: ResultsState = ResultsState.Idle,
) {
    /** The field now reads [query]. Editing a submitted query leaves its results behind. */
    fun withQuery(query: String): SearchState =
        if (query == this.query) {
            this
        } else {
            copy(
                query = query,
                results = ResultsState.Idle,
                suggestions = if (query.isBlank()) SuggestionsState.Idle else suggestions,
            )
        }

    /**
     * Suggestions for [query] were asked for. The previous ones stay on screen while the next ones load,
     * rather than flashing a spinner; a blank query has none.
     */
    fun awaitingSuggestions(query: String): SearchState =
        when {
            query.isEmpty() -> copy(suggestions = SuggestionsState.Idle)
            suggestions is SuggestionsState.Loaded -> this
            else -> copy(suggestions = SuggestionsState.Loading)
        }

    /** The suggestions for [query], unless the user typed on while they loaded. */
    fun withSuggestions(
        query: String,
        result: Result<List<ArticleSummary>, DataError>,
    ): SearchState =
        if (this.query.trim() != query) {
            this
        } else {
            copy(
                suggestions =
                    result.fold(
                        onSuccess = { SuggestionsState.Loaded(it.toImmutableList()) },
                        onFailure = { SuggestionsState.Failed(it) },
                    ),
            )
        }

    fun searching(
        query: String,
        replaceQuery: Boolean,
    ): SearchState =
        copy(
            query = if (replaceQuery) query else this.query,
            results = ResultsState.Loading(query),
        )

    /** The first page for [query], unless the query was edited or resubmitted while it loaded. */
    fun withResults(
        query: String,
        result: Result<Paged<ArticleSummary>, DataError>,
    ): SearchState {
        val current = results
        if (current !is ResultsState.Loading || current.query != query) return this
        return copy(
            results =
                result.fold(
                    onSuccess = { ResultsState.Loaded(query, it.items, it.continuation) },
                    onFailure = { ResultsState.Failed(query, it) },
                ),
        )
    }

    fun loadingMore(
        query: String,
        token: String,
    ): SearchState = withLoaded(query, token) { it.copy(isLoadingMore = true, loadMoreFailed = false) }

    /** Appends the page fetched with [token]; a repeat of an earlier result is dropped. */
    fun withNextPage(
        query: String,
        token: String,
        result: Result<Paged<ArticleSummary>, DataError>,
    ): SearchState =
        withLoaded(query, token) { loaded ->
            when (result) {
                is Result.Success -> {
                    loaded.copy(
                        items = (loaded.items + result.data.items).distinctBy { it.title }.toImmutableList(),
                        continuation = result.data.continuation,
                        isLoadingMore = false,
                    )
                }

                is Result.Failure -> {
                    loaded.copy(isLoadingMore = false, loadMoreFailed = true)
                }
            }
        }

    /** Applies [transform] only if the results are still the page of [query] that is waiting on [token]. */
    private inline fun withLoaded(
        query: String,
        token: String?,
        transform: (ResultsState.Loaded) -> ResultsState.Loaded,
    ): SearchState {
        val current = results
        return if (current is ResultsState.Loaded && current.query == query && current.continuation == token) {
            copy(results = transform(current))
        } else {
            this
        }
    }
}

@Immutable
sealed interface SuggestionsState {
    data object Idle : SuggestionsState

    data object Loading : SuggestionsState

    data class Loaded(
        val items: ImmutableList<ArticleSummary>,
    ) : SuggestionsState

    data class Failed(
        val error: DataError,
    ) : SuggestionsState
}

/** Full-text results of the submitted [query]. */
@Immutable
sealed interface ResultsState {
    /** Nothing was submitted, or the query was edited since. */
    data object Idle : ResultsState

    data class Loading(
        val query: String,
    ) : ResultsState

    data class Failed(
        val query: String,
        val error: DataError,
    ) : ResultsState

    /** [continuation] is the token for the next page, or `null` when this is the last one. */
    data class Loaded(
        val query: String,
        val items: ImmutableList<ArticleSummary>,
        val continuation: String?,
        val isLoadingMore: Boolean = false,
        val loadMoreFailed: Boolean = false,
    ) : ResultsState
}

/**
 * The query text itself lives in the screen's text field, which reports each edit as [QueryChanged];
 * anything that changes it from outside (a recent search, the clear button) edits the field first.
 */
sealed interface SearchAction {
    data class QueryChanged(
        val query: String,
    ) : SearchAction

    /** The keyboard's search key: runs a full-text search for the current query. */
    data object Submit : SearchAction

    data class SelectRecent(
        val query: String,
    ) : SearchAction

    data class RemoveRecent(
        val query: String,
    ) : SearchAction

    data object ClearRecents : SearchAction

    data class SuggestionClicked(
        val title: String,
    ) : SearchAction

    data class ResultClicked(
        val title: String,
    ) : SearchAction

    data object RetrySuggestions : SearchAction

    data object RetryResults : SearchAction

    /** Loads the next page of results, or retries it after it failed. Ignored when none is left. */
    data object LoadMore : SearchAction
}

sealed interface SearchEffect {
    data class OpenArticle(
        val title: String,
    ) : SearchEffect
}
