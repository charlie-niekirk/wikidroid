package dev.cniekirk.wikidroid.feature.search

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
)

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
