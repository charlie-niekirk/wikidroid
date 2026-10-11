package dev.cniekirk.wikidroid.feature.search

import androidx.lifecycle.ViewModel
import dev.cniekirk.wikidroid.core.data.SearchRepository
import dev.cniekirk.wikidroid.core.domain.search.RunSearchUseCase
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
    private val runSearchUseCase: RunSearchUseCase,
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
                        .collectLatest { query ->
                            reduce { state.awaitingSuggestions(query) }
                            if (query.isEmpty()) return@collectLatest
                            val result = repository.autocomplete(query)
                            reduce { state.withSuggestions(query, result) }
                        }
                }
            }
        }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.QueryChanged -> queryChanged(action.query)
            SearchAction.Submit -> intent { runSearch(state.query.trim()) }
            is SearchAction.SelectRecent -> runSearch(action.query, replaceQuery = true)
            is SearchAction.RemoveRecent -> intent { repository.removeRecentSearch(action.query) }
            SearchAction.ClearRecents -> intent { repository.clearRecentSearches() }
            is SearchAction.SuggestionClicked -> suggestionClicked(action.title)
            is SearchAction.ResultClicked -> intent { postSideEffect(SearchEffect.OpenArticle(action.title)) }
            SearchAction.RetrySuggestions -> retrySuggestions()
            SearchAction.RetryResults -> retryResults()
            SearchAction.LoadMore -> loadMore()
        }
    }

    private fun queryChanged(query: String) =
        intent {
            reduce { state.withQuery(query) }
            queries.value = query
        }

    private fun retrySuggestions() =
        intent {
            val query = state.query.trim()
            reduce { state.awaitingSuggestions(query) }
            if (query.isEmpty()) return@intent
            val result = repository.autocomplete(query)
            reduce { state.withSuggestions(query, result) }
        }

    /** Runs a full-text search for [query]; [replaceQuery] also puts it in the search field's state. */
    private fun runSearch(
        query: String,
        replaceQuery: Boolean = false,
    ) = intent {
        if (query.isEmpty()) return@intent
        reduce { state.searching(query, replaceQuery) }
        val result = runSearchUseCase(query)
        reduce { state.withResults(query, result) }
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
                reduce { state.loadingMore(current.query, token) }
                val result = repository.search(current.query, token)
                reduce { state.withNextPage(current.query, token, result) }
            } finally {
                pageLock.unlock()
            }
        }

    private companion object {
        const val AUTOCOMPLETE_DEBOUNCE_MILLIS = 250L
    }
}
