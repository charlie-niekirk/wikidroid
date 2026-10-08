package dev.cniekirk.wikidroid.feature.search

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Paged
import dev.cniekirk.wikidroid.core.testing.MainDispatcherRule
import dev.cniekirk.wikidroid.core.testing.fake.FakeSearchRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.orbitmvi.orbit.test.OrbitTestContext
import org.orbitmvi.orbit.test.test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Several of these assert on the final state after the scheduler goes idle rather than on every emission,
 * because the recent-searches collector and the intents interleave in an order that isn't part of the contract.
 */
class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSearchRepository()
    private val viewModel = SearchViewModel(repository)

    private val state get() = viewModel.container.stateFlow.value

    private fun titles(vararg titles: String): ImmutableList<ArticleSummary> =
        titles.map { ArticleSummary(title = it) }.toImmutableList()

    private fun page(
        continuation: String?,
        vararg titles: String,
    ): Result<Paged<ArticleSummary>, DataError> = Result.Success(Paged(titles(*titles), continuation))

    private suspend fun TestScope.search(
        body: suspend OrbitTestContext<SearchState, SearchEffect, SearchViewModel>.() -> Unit,
    ) {
        viewModel.test(this) {
            runOnCreate()
            body()
            cancelAndIgnoreRemainingItems()
        }
    }

    private fun OrbitTestContext<SearchState, SearchEffect, SearchViewModel>.type(query: String) {
        containerHost.onAction(SearchAction.QueryChanged(query))
    }

    // region recent searches

    @Test
    fun showsTheRecentSearchesNewestFirst() =
        runTest {
            repository.saveRecentSearch("creeper")
            repository.saveRecentSearch("diamond")

            search {
                runCurrent()

                assertThat(state.recentSearches).containsExactly("diamond", "creeper").inOrder()
            }
        }

    @Test
    fun removesOneRecentSearchOrAllOfThem() =
        runTest {
            repository.saveRecentSearch("creeper")
            repository.saveRecentSearch("diamond")

            search {
                runCurrent()

                containerHost.onAction(SearchAction.RemoveRecent("diamond"))
                runCurrent()
                assertThat(state.recentSearches).containsExactly("creeper")

                containerHost.onAction(SearchAction.ClearRecents)
                runCurrent()
                assertThat(state.recentSearches).isEmpty()
            }
        }

    // endregion

    // region suggestions

    @Test
    fun typingFastAsksForSuggestionsOnceAfterTheDebounce() =
        runTest {
            repository.autocompleteHandler = { Result.Success(titles("Diamond", "Diamond Ore")) }

            search {
                type("d")
                advanceTimeBy(100.milliseconds)
                type("di")
                advanceTimeBy(100.milliseconds)
                type("dia")
                advanceTimeBy(249.milliseconds)
                runCurrent()
                assertThat(repository.autocompleteQueries).isEmpty()

                advanceTimeBy(1.milliseconds)
                runCurrent()

                assertThat(repository.autocompleteQueries).containsExactly("dia")
                assertThat(state.suggestions).isEqualTo(SuggestionsState.Loaded(titles("Diamond", "Diamond Ore")))
            }
        }

    @Test
    fun suggestionsAreRequestedForTheTrimmedQuery() =
        runTest {
            search {
                type("  diamond ")
                advanceTimeBy(250.milliseconds)
                runCurrent()

                assertThat(repository.autocompleteQueries).containsExactly("diamond")
            }
        }

    @Test
    fun clearingTheQueryClearsTheSuggestionsImmediately() =
        runTest {
            repository.autocompleteHandler = { Result.Success(titles("Diamond")) }

            search {
                type("dia")
                advanceTimeBy(250.milliseconds)
                runCurrent()
                assertThat(state.suggestions).isInstanceOf(SuggestionsState.Loaded::class.java)

                type("")
                runCurrent()

                assertThat(state.suggestions).isEqualTo(SuggestionsState.Idle)
                // The blank query still passes through the debounce, but asks for nothing.
                advanceTimeBy(250.milliseconds)
                runCurrent()
                assertThat(state.suggestions).isEqualTo(SuggestionsState.Idle)
                assertThat(repository.autocompleteQueries).containsExactly("dia")
            }
        }

    @Test
    fun suggestionsForAnEarlierQueryAreDroppedWhenTheUserTypedOn() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.autocompleteHandler = { query ->
                if (query == "dia") gate.await()
                Result.Success(titles("Result for $query"))
            }

            search {
                type("dia")
                advanceTimeBy(250.milliseconds)
                runCurrent()
                assertThat(state.suggestions).isEqualTo(SuggestionsState.Loading)

                type("diam")
                runCurrent()
                gate.complete(Unit)
                runCurrent()

                // Nothing from "dia" landed, and "diam" hasn't passed its debounce yet.
                assertThat(state.suggestions).isEqualTo(SuggestionsState.Loading)

                advanceTimeBy(250.milliseconds)
                runCurrent()
                assertThat(state.suggestions).isEqualTo(SuggestionsState.Loaded(titles("Result for diam")))
            }
        }

    @Test
    fun aFailedSuggestionRequestCanBeRetried() =
        runTest {
            repository.autocompleteHandler = { Result.Failure(DataError.Network()) }

            search {
                type("dia")
                advanceTimeBy(250.milliseconds)
                runCurrent()
                assertThat(state.suggestions).isEqualTo(SuggestionsState.Failed(DataError.Network()))

                repository.autocompleteHandler = { Result.Success(titles("Diamond")) }
                containerHost.onAction(SearchAction.RetrySuggestions)
                runCurrent()

                assertThat(state.suggestions).isEqualTo(SuggestionsState.Loaded(titles("Diamond")))
            }
        }

    // endregion

    // region full-text results

    @Test
    fun submittingLoadsTheFirstPageOfResultsAndRemembersTheSearch() =
        runTest {
            repository.searchHandler = { _, _ -> page("next", "Creeper", "Creeper Spawn Egg") }

            search {
                type("creeper")
                runCurrent()
                containerHost.onAction(SearchAction.Submit)
                runCurrent()

                assertThat(state.results)
                    .isEqualTo(
                        ResultsState.Loaded("creeper", titles("Creeper", "Creeper Spawn Egg"), continuation = "next"),
                    )
                assertThat(repository.searchRequests).containsExactly("creeper" to null)
                assertThat(state.recentSearches).containsExactly("creeper")
                // The text field owns the text, so submitting must not rewrite it.
                assertThat(state.query).isEqualTo("creeper")
            }
        }

    @Test
    fun resultsAreLoadingUntilThePageArrives() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.searchHandler = { _, _ ->
                gate.await()
                page(null, "Creeper")
            }

            search {
                type("creeper")
                runCurrent()
                containerHost.onAction(SearchAction.Submit)
                runCurrent()
                assertThat(state.results).isEqualTo(ResultsState.Loading("creeper"))

                gate.complete(Unit)
                runCurrent()

                assertThat(
                    state.results,
                ).isEqualTo(ResultsState.Loaded("creeper", titles("Creeper"), continuation = null))
            }
        }

    @Test
    fun submittingABlankQueryDoesNothing() =
        runTest {
            search {
                type("   ")
                runCurrent()
                containerHost.onAction(SearchAction.Submit)
                runCurrent()

                assertThat(state.query).isEqualTo("   ")
                assertThat(repository.searchRequests).isEmpty()
                assertThat(state.results).isEqualTo(ResultsState.Idle)
            }
        }

    @Test
    fun editingTheQueryLeavesTheResultsBehind() =
        runTest {
            repository.searchHandler = { _, _ -> page(null, "Creeper") }

            search {
                type("creeper")
                runCurrent()
                containerHost.onAction(SearchAction.Submit)
                runCurrent()
                assertThat(state.results).isInstanceOf(ResultsState.Loaded::class.java)

                type("creepers")
                runCurrent()

                assertThat(state.results).isEqualTo(ResultsState.Idle)
            }
        }

    @Test
    fun reportingTheSameQueryAgainKeepsTheResults() =
        runTest {
            repository.searchHandler = { _, _ -> page(null, "Creeper") }

            search {
                type("creeper")
                runCurrent()
                containerHost.onAction(SearchAction.Submit)
                runCurrent()

                type("creeper")
                runCurrent()

                assertThat(state.results).isInstanceOf(ResultsState.Loaded::class.java)
            }
        }

    @Test
    fun resultsOfAnEditedQueryAreDroppedWhenTheyArriveLate() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.searchHandler = { _, _ ->
                gate.await()
                page(null, "Creeper")
            }

            search {
                type("creeper")
                runCurrent()
                containerHost.onAction(SearchAction.Submit)
                runCurrent()

                type("creepers")
                runCurrent()
                gate.complete(Unit)
                runCurrent()

                assertThat(state.results).isEqualTo(ResultsState.Idle)
            }
        }

    @Test
    fun aFailedSearchCanBeRetried() =
        runTest {
            repository.searchHandler = { _, _ -> Result.Failure(DataError.Network()) }

            search {
                type("creeper")
                runCurrent()
                containerHost.onAction(SearchAction.Submit)
                runCurrent()
                assertThat(state.results).isEqualTo(ResultsState.Failed("creeper", DataError.Network()))

                repository.searchHandler = { _, _ -> page(null, "Creeper") }
                containerHost.onAction(SearchAction.RetryResults)
                runCurrent()

                assertThat(
                    state.results,
                ).isEqualTo(ResultsState.Loaded("creeper", titles("Creeper"), continuation = null))
            }
        }

    @Test
    fun retryResultsDoesNothingUnlessTheSearchFailed() =
        runTest {
            search {
                containerHost.onAction(SearchAction.RetryResults)
                runCurrent()

                assertThat(repository.searchRequests).isEmpty()
                assertThat(state.results).isEqualTo(ResultsState.Idle)
            }
        }

    // endregion

    // region paging

    private fun TestScope.submit(query: String) {
        viewModel.onAction(SearchAction.QueryChanged(query))
        runCurrent()
        viewModel.onAction(SearchAction.Submit)
        runCurrent()
    }

    @Test
    fun loadMoreAppendsTheNextPageAndSkipsRepeats() =
        runTest {
            repository.searchHandler = { _, token ->
                if (token == null) page("two", "Creeper", "Zombie") else page(null, "Zombie", "Skeleton")
            }

            search {
                submit("mob")

                containerHost.onAction(SearchAction.LoadMore)
                runCurrent()

                assertThat(state.results)
                    .isEqualTo(
                        ResultsState.Loaded("mob", titles("Creeper", "Zombie", "Skeleton"), continuation = null),
                    )
                assertThat(repository.searchRequests).containsExactly("mob" to null, "mob" to "two").inOrder()
            }
        }

    @Test
    fun loadMoreStopsOnceTheLastPageIsLoaded() =
        runTest {
            repository.searchHandler = { _, _ -> page(null, "Creeper") }

            search {
                submit("mob")

                containerHost.onAction(SearchAction.LoadMore)
                runCurrent()

                assertThat(repository.searchRequests).hasSize(1)
            }
        }

    @Test
    fun loadMoreIsIgnoredWithoutResults() =
        runTest {
            search {
                containerHost.onAction(SearchAction.LoadMore)
                runCurrent()

                assertThat(repository.searchRequests).isEmpty()
                assertThat(state.results).isEqualTo(ResultsState.Idle)
            }
        }

    @Test
    fun repeatedLoadMoreRequestsFetchThePageOnce() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.searchHandler = { _, token ->
                if (token == null) {
                    page("two", "Creeper")
                } else {
                    gate.await()
                    page(null, "Zombie")
                }
            }

            search {
                submit("mob")

                containerHost.onAction(SearchAction.LoadMore)
                runCurrent()
                assertThat((state.results as ResultsState.Loaded).isLoadingMore).isTrue()
                containerHost.onAction(SearchAction.LoadMore)
                containerHost.onAction(SearchAction.LoadMore)
                runCurrent()
                gate.complete(Unit)
                runCurrent()

                assertThat(repository.searchRequests).containsExactly("mob" to null, "mob" to "two").inOrder()
                assertThat(
                    state.results,
                ).isEqualTo(ResultsState.Loaded("mob", titles("Creeper", "Zombie"), continuation = null))
            }
        }

    @Test
    fun aFailedPageKeepsTheResultsAndCanBeRetried() =
        runTest {
            repository.searchHandler = { _, token ->
                if (token == null) page("two", "Creeper") else Result.Failure(DataError.Network())
            }

            search {
                submit("mob")

                containerHost.onAction(SearchAction.LoadMore)
                runCurrent()
                assertThat(state.results)
                    .isEqualTo(
                        ResultsState.Loaded(
                            "mob",
                            titles("Creeper"),
                            continuation = "two",
                            isLoadingMore = false,
                            loadMoreFailed = true,
                        ),
                    )

                repository.searchHandler = { _, _ -> page(null, "Zombie") }
                containerHost.onAction(SearchAction.LoadMore)
                runCurrent()

                assertThat(
                    state.results,
                ).isEqualTo(ResultsState.Loaded("mob", titles("Creeper", "Zombie"), continuation = null))
            }
        }

    @Test
    fun aPageForAnEditedQueryIsDropped() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.searchHandler = { _, token ->
                if (token == null) {
                    page("two", "Creeper")
                } else {
                    gate.await()
                    page(null, "Zombie")
                }
            }

            search {
                submit("mob")
                containerHost.onAction(SearchAction.LoadMore)
                runCurrent()

                type("mobs")
                runCurrent()
                gate.complete(Unit)
                runCurrent()

                assertThat(state.results).isEqualTo(ResultsState.Idle)
            }
        }

    // endregion

    // region selecting

    @Test
    fun selectingARecentSearchRunsItAgain() =
        runTest {
            repository.saveRecentSearch("diamond")
            repository.saveRecentSearch("creeper")
            repository.searchHandler = { query, _ -> page(null, query) }

            search {
                containerHost.onAction(SearchAction.SelectRecent("diamond"))
                runCurrent()

                assertThat(state.query).isEqualTo("diamond")
                assertThat(
                    state.results,
                ).isEqualTo(ResultsState.Loaded("diamond", titles("diamond"), continuation = null))
                // Searching again moves it to the front.
                assertThat(state.recentSearches).containsExactly("diamond", "creeper").inOrder()
            }
        }

    @Test
    fun selectingARecentSearchSurvivesTheTextFieldReportingIt() =
        runTest {
            repository.searchHandler = { query, _ -> page(null, query) }

            search {
                // The screen edits its text field and sends the action; the field's own report follows.
                containerHost.onAction(SearchAction.SelectRecent("diamond"))
                type("diamond")
                runCurrent()

                assertThat(
                    state.results,
                ).isEqualTo(ResultsState.Loaded("diamond", titles("diamond"), continuation = null))
            }
        }

    @Test
    fun clickingASuggestionRemembersItAndOpensIt() =
        runTest {
            search {
                containerHost.onAction(SearchAction.SuggestionClicked("Diamond"))
                runCurrent()

                expectSideEffect(SearchEffect.OpenArticle("Diamond"))
                assertThat(state.recentSearches).containsExactly("Diamond")
            }
        }

    @Test
    fun clickingAResultOpensItWithoutChangingTheRecentSearches() =
        runTest {
            search {
                containerHost.onAction(SearchAction.ResultClicked("Creeper"))
                runCurrent()

                expectSideEffect(SearchEffect.OpenArticle("Creeper"))
                assertThat(state.recentSearches).isEmpty()
            }
        }

    // endregion

    @Test
    fun startsWithNothingTyped() {
        assertThat(state).isEqualTo(SearchState(recentSearches = persistentListOf()))
    }
}
