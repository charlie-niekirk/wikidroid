package dev.cniekirk.wikidroid.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class SearchScreenTest : ComposeTest() {
    private val actions = mutableListOf<SearchAction>()

    private fun setScreen(state: SearchState) {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                SearchScreen(state = state, onAction = { actions += it })
            }
        }
    }

    /** Everything except the field's own reports, which also arrive once when the screen first composes. */
    private val userActions get() = actions.filterNot { it is SearchAction.QueryChanged }

    private val lastReportedQuery get() = actions.filterIsInstance<SearchAction.QueryChanged>().last().query

    private val creeper = ArticleSummary(title = "Creeper", description = "A hostile mob.")
    private val spawnEgg = ArticleSummary(title = "Creeper Spawn Egg")
    private val chargedCreeper = ArticleSummary(title = "Charged creeper")

    // region field

    @Test
    fun theFieldStartsWithTheCurrentQuery() {
        setScreen(SearchState(query = "creeper"))

        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).assertTextContains("creeper")
    }

    @Test
    fun showsAHintWhenEmpty() {
        setScreen(SearchState())

        composeRule.onNodeWithText("Search the wiki", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun typingReportsEachEdit() {
        setScreen(SearchState())

        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("dia")
        composeRule.waitForIdle()

        assertThat(lastReportedQuery).isEqualTo("dia")
    }

    @Test
    fun theKeyboardsSearchKeySubmits() {
        setScreen(SearchState(query = "creeper"))

        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).performImeAction()

        assertThat(actions).contains(SearchAction.Submit)
    }

    @Test
    fun theClearButtonEmptiesTheField() {
        setScreen(SearchState(query = "creeper"))

        composeRule.onNodeWithContentDescription("Clear search").performClick()
        composeRule.waitForIdle()

        assertThat(lastReportedQuery).isEmpty()
        composeRule.onNodeWithContentDescription("Clear search").assertDoesNotExist()
    }

    @Test
    fun theClearButtonIsHiddenWhenThereIsNothingToClear() {
        setScreen(SearchState())

        composeRule.onNodeWithContentDescription("Clear search").assertDoesNotExist()
    }

    // endregion

    // region recent searches

    @Test
    fun invitesYouToSearchWhenThereIsNoHistory() {
        setScreen(SearchState())

        composeRule.onNodeWithText("What are you looking for?").assertIsDisplayed()
        composeRule.onNodeWithText("Search for blocks, mobs, items, commands and more.").assertIsDisplayed()
    }

    @Test
    fun listsTheRecentSearches() {
        setScreen(SearchState(recentSearches = persistentListOf("diamond", "creeper")))

        composeRule.onNodeWithText("Recent searches").assertIsDisplayed()
        composeRule.onNodeWithText("diamond").assertIsDisplayed()
        composeRule.onNodeWithText("creeper").assertIsDisplayed()
    }

    @Test
    fun tappingARecentSearchFillsTheFieldAndSearches() {
        setScreen(SearchState(recentSearches = persistentListOf("diamond", "creeper")))

        composeRule.onNodeWithText("creeper").performClick()
        composeRule.waitForIdle()

        assertThat(userActions).containsExactly(SearchAction.SelectRecent("creeper"))
        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).assertTextContains("creeper")
        assertThat(lastReportedQuery).isEqualTo("creeper")
    }

    @Test
    fun aRecentSearchCanBeRemoved() {
        setScreen(SearchState(recentSearches = persistentListOf("diamond", "creeper")))

        composeRule.onNodeWithContentDescription("Remove diamond from recent searches").performClick()

        assertThat(userActions).containsExactly(SearchAction.RemoveRecent("diamond"))
    }

    @Test
    fun theHistoryCanBeCleared() {
        setScreen(SearchState(recentSearches = persistentListOf("diamond")))

        composeRule.onNodeWithText("Clear").performClick()

        assertThat(userActions).containsExactly(SearchAction.ClearRecents)
    }

    // endregion

    // region suggestions

    private val withSuggestions =
        SearchState(
            query = "cree",
            suggestions = SuggestionsState.Loaded(persistentListOf(creeper, spawnEgg)),
        )

    @Test
    fun suggestionsReplaceTheHistoryWhileTyping() {
        setScreen(withSuggestions.copy(recentSearches = persistentListOf("diamond")))

        composeRule.onNodeWithText("Creeper").assertIsDisplayed()
        composeRule.onNodeWithText("A hostile mob.").assertIsDisplayed()
        composeRule.onNodeWithText("Creeper Spawn Egg").assertIsDisplayed()
        composeRule.onNodeWithText("Recent searches").assertDoesNotExist()
    }

    @Test
    fun tappingASuggestionOpensIt() {
        setScreen(withSuggestions)

        composeRule.onNodeWithText("Creeper Spawn Egg").performClick()

        assertThat(actions).contains(SearchAction.SuggestionClicked("Creeper Spawn Egg"))
    }

    @Test
    fun theFirstRowSearchesForTheQuery() {
        setScreen(withSuggestions)

        composeRule.onNodeWithText("Search for “cree”").performClick()

        assertThat(actions).contains(SearchAction.Submit)
    }

    @Test
    fun theSearchRowIsThereBeforeAnySuggestionArrives() {
        setScreen(SearchState(query = "cree", suggestions = SuggestionsState.Loading))

        composeRule.onNodeWithText("Search for “cree”").assertIsDisplayed()
    }

    @Test
    fun theSearchRowUsesTheTrimmedQuery() {
        setScreen(SearchState(query = "  cree "))

        composeRule.onNodeWithText("Search for “cree”").assertIsDisplayed()
    }

    @Test
    fun aSuggestionFailureKeepsTheSearchRowAndOffersARetry() {
        setScreen(SearchState(query = "cree", suggestions = SuggestionsState.Failed(DataError.Network())))

        composeRule.onNodeWithText("Search for “cree”").assertIsDisplayed()
        composeRule.onNodeWithText("Couldn't load suggestions.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertThat(actions).contains(SearchAction.RetrySuggestions)
    }

    // endregion

    // region results

    private fun resultsState(results: ResultsState) = SearchState(query = "creeper", results = results)

    @Test
    fun showsASpinnerWhileSearching() {
        setScreen(resultsState(ResultsState.Loading("creeper")))

        composeRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun showsTheResults() {
        setScreen(
            resultsState(
                ResultsState.Loaded("creeper", persistentListOf(creeper, chargedCreeper), continuation = null),
            ),
        )

        composeRule.onNodeWithText("Creeper").assertIsDisplayed()
        composeRule.onNodeWithText("Charged creeper").assertIsDisplayed()
    }

    @Test
    fun resultsReplaceTheSuggestions() {
        setScreen(
            resultsState(ResultsState.Loaded("creeper", persistentListOf(creeper), continuation = null))
                .copy(suggestions = SuggestionsState.Loaded(persistentListOf(spawnEgg))),
        )

        composeRule.onNodeWithText("Creeper Spawn Egg").assertDoesNotExist()
        composeRule.onNodeWithText("Search for “creeper”").assertDoesNotExist()
    }

    @Test
    fun tappingAResultOpensIt() {
        setScreen(resultsState(ResultsState.Loaded("creeper", persistentListOf(creeper), continuation = null)))

        composeRule.onNodeWithText("Creeper").performClick()

        assertThat(actions).contains(SearchAction.ResultClicked("Creeper"))
    }

    @Test
    fun saysWhenNothingMatches() {
        setScreen(resultsState(ResultsState.Loaded("creeper", persistentListOf(), continuation = null)))

        composeRule.onNodeWithText("No results").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing on the wiki matches “creeper”.").assertIsDisplayed()
    }

    @Test
    fun aFailedSearchOffersARetry() {
        setScreen(resultsState(ResultsState.Failed("creeper", DataError.Network())))

        composeRule.onNodeWithText("You appear to be offline. Check your connection and try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertThat(userActions).containsExactly(SearchAction.RetryResults)
    }

    @Test
    fun asksForTheNextPageWhenTheEndIsInView() {
        setScreen(resultsState(ResultsState.Loaded("creeper", persistentListOf(creeper), continuation = "next")))

        composeRule.waitForIdle()

        assertThat(actions).contains(SearchAction.LoadMore)
    }

    @Test
    fun doesNotAskForMoreOnTheLastPage() {
        setScreen(resultsState(ResultsState.Loaded("creeper", persistentListOf(creeper), continuation = null)))

        composeRule.waitForIdle()

        assertThat(actions.filterIsInstance<SearchAction.LoadMore>()).isEmpty()
    }

    @Test
    fun showsASpinnerWhileMoreLoads() {
        setScreen(
            resultsState(
                ResultsState.Loaded("creeper", persistentListOf(creeper), continuation = "next", isLoadingMore = true),
            ),
        )

        composeRule.onNodeWithContentDescription("Loading more").assertIsDisplayed()
    }

    @Test
    fun aFailedPageOffersARetryWithoutLoopingOnItsOwn() {
        setScreen(
            resultsState(
                ResultsState.Loaded("creeper", persistentListOf(creeper), continuation = "next", loadMoreFailed = true),
            ),
        )

        composeRule.waitForIdle()
        assertThat(actions.filterIsInstance<SearchAction.LoadMore>()).isEmpty()
        composeRule.onNodeWithText("Try again").performClick()

        assertThat(actions).contains(SearchAction.LoadMore)
    }

    // endregion
}
