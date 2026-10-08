package dev.cniekirk.wikidroid.feature.search

import androidx.lifecycle.ViewModel
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.fold
import dev.cniekirk.wikidroid.core.data.SearchRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.syntax.Syntax
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * Drives the Search tab: recent searches, debounced title suggestions while typing, and paged full-text
 * results once a search is submitted.
 *
 * Several intents can be in flight at once (the text field reports every keystroke), so any decision that
 * depends on the current state is made inside `reduce`, which sees the latest state, rather than before it.
 */
@OptIn(FlowPreview::class)
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SearchViewModel(
    private val repository: SearchRepository,
) : ViewModel(),
    OrbitContainerHost<SearchState, SearchState, SearchEffect> {
    private val queries = MutableStateFlow("")

    /** Held while a results page is being appended, so a second request for it is dropped, not queued. */
    private val pageLock = Mutex()

    override val container =
        orbitContainer<SearchState, SearchEffect>(SearchState()) {
            coroutineScope {
                launch {
                    repository.recentSearches.collect { recent ->
                        reduce { state.copy(recentSearches = recent.toImmutableList()) }
                    }
                }
                launch {
                    queries
                        .debounce(AUTOCOMPLETE_DEBOUNCE_MILLIS)
                        .map { it.trim() }
                        .distinctUntilChanged()
                        .collectLatest { query -> suggest(query) }
                }
            }
        }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.QueryChanged -> queryChanged(action.query)
            SearchAction.Submit -> submit()
            is SearchAction.SelectRecent -> selectRecent(action.query)
            is SearchAction.RemoveRecent -> intent { repository.removeRecentSearch(action.query) }
            SearchAction.ClearRecents -> intent { repository.clearRecentSearches() }
            is SearchAction.SuggestionClicked -> suggestionClicked(action.title)
            is SearchAction.ResultClicked -> intent { postSideEffect(SearchEffect.OpenArticle(action.title)) }
            SearchAction.RetrySuggestions -> intent { suggest(state.query.trim()) }
            SearchAction.RetryResults -> retryResults()
            SearchAction.LoadMore -> loadMore()
        }
    }

    private fun queryChanged(query: String) =
        intent {
            reduce {
                if (query == state.query) {
                    state
                } else {
                    state.copy(
                        query = query,
                        // Editing a submitted query leaves its results behind.
                        results = ResultsState.Idle,
                        suggestions = if (query.isBlank()) SuggestionsState.Idle else state.suggestions,
                    )
                }
            }
            queries.value = query
        }

    private fun submit() = intent { runSearch(state.query.trim()) }

    private fun selectRecent(query: String) =
        intent {
            reduce { state.copy(query = query) }
            runSearch(query)
        }

    private fun suggestionClicked(title: String) =
        intent {
            repository.saveRecentSearch(title)
            postSideEffect(SearchEffect.OpenArticle(title))
        }

    private fun retryResults() =
        intent {
            val failed = state.results as? ResultsState.Failed ?: return@intent
            runSearch(failed.query)
        }

    private fun loadMore() =
        intent {
            if (!pageLock.tryLock()) return@intent
            try {
                // Read the token under the lock: a request that waited behind another one must not repeat its page.
                val current = state.results as? ResultsState.Loaded ?: return@intent
                val token = current.continuation ?: return@intent
                reduce {
                    state.withLoaded(
                        current.query,
                        token,
                    ) { it.copy(isLoadingMore = true, loadMoreFailed = false) }
                }
                when (val result = repository.search(current.query, token)) {
                    is Result.Success -> {
                        reduce {
                            state.withLoaded(current.query, token) {
                                it.copy(
                                    items =
                                        (it.items + result.data.items)
                                            .distinctBy { page ->
                                                page.title
                                            }.toImmutableList(),
                                    continuation = result.data.continuation,
                                    isLoadingMore = false,
                                )
                            }
                        }
                    }

                    is Result.Failure -> {
                        reduce {
                            state.withLoaded(
                                current.query,
                                token,
                            ) { it.copy(isLoadingMore = false, loadMoreFailed = true) }
                        }
                    }
                }
            } finally {
                pageLock.unlock()
            }
        }

    private suspend fun Syntax<SearchState, SearchEffect>.suggest(query: String) {
        if (query.isEmpty()) {
            reduce { state.copy(suggestions = SuggestionsState.Idle) }
            return
        }
        // Keep showing the previous suggestions while the next ones load, rather than flashing a spinner.
        reduce {
            if (state.suggestions is SuggestionsState.Loaded) {
                state
            } else {
                state.copy(
                    suggestions = SuggestionsState.Loading,
                )
            }
        }
        val result = repository.autocomplete(query)
        reduce {
            if (state.query.trim() != query) {
                // The user typed on while this was loading.
                state
            } else {
                state.copy(
                    suggestions =
                        result.fold(
                            onSuccess = { SuggestionsState.Loaded(it.toImmutableList()) },
                            onFailure = { SuggestionsState.Failed(it) },
                        ),
                )
            }
        }
    }

    private suspend fun Syntax<SearchState, SearchEffect>.runSearch(query: String) {
        if (query.isEmpty()) return
        reduce { state.copy(results = ResultsState.Loading(query)) }
        repository.saveRecentSearch(query)
        val result = repository.search(query, continuation = null)
        reduce {
            val current = state.results
            if (current !is ResultsState.Loading || current.query != query) {
                // Edited or resubmitted while this was loading.
                state
            } else {
                state.copy(
                    results =
                        result.fold(
                            onSuccess = { ResultsState.Loaded(query, it.items, it.continuation) },
                            onFailure = { ResultsState.Failed(query, it) },
                        ),
                )
            }
        }
    }

    private companion object {
        const val AUTOCOMPLETE_DEBOUNCE_MILLIS = 250L
    }
}

/** Applies [transform] only if the results are still the page of [query] that is waiting on [token]. */
private inline fun SearchState.withLoaded(
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
