package dev.cniekirk.wikidroid.feature.search

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Paged
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class SearchStateTest {
    private fun titles(vararg titles: String) =
        persistentListOf(*titles.map { ArticleSummary(title = it) }.toTypedArray())

    @Test
    fun suggestionsForAQueryTheUserTypedPastAreDropped() {
        val typing = SearchState(query = "diamond ore")

        val after = typing.withSuggestions("diamond", Result.Success(titles("Diamond")))

        assertThat(after).isEqualTo(typing)
    }

    @Test
    fun loadedSuggestionsStayWhileTheNextOnesLoad() {
        val shown = SearchState(query = "dia", suggestions = SuggestionsState.Loaded(titles("Diamond")))

        assertThat(shown.awaitingSuggestions("diam")).isEqualTo(shown)
        assertThat(shown.awaitingSuggestions("").suggestions).isEqualTo(SuggestionsState.Idle)
    }

    @Test
    fun resultsArriveOnlyForTheSearchStillWaiting() {
        val waiting = SearchState(query = "creeper").searching("creeper", replaceQuery = false)
        val page = Result.Success(Paged(titles("Creeper"), continuation = "next"))

        assertThat(waiting.withResults("creeper", page).results)
            .isEqualTo(ResultsState.Loaded("creeper", titles("Creeper"), continuation = "next"))
        assertThat(waiting.withResults("zombie", page)).isEqualTo(waiting)
    }

    @Test
    fun aNextPageAppendsWithoutRepeatsAndAFailureKeepsThePage() {
        val loaded =
            SearchState(results = ResultsState.Loaded("creeper", titles("Creeper"), continuation = "next"))
                .loadingMore("creeper", "next")

        val appended =
            loaded.withNextPage("creeper", "next", Result.Success(Paged(titles("Creeper", "Creeper Head"), null)))
        val failed = loaded.withNextPage("creeper", "next", Result.Failure(DataError.Network()))

        assertThat(appended.results)
            .isEqualTo(ResultsState.Loaded("creeper", titles("Creeper", "Creeper Head"), continuation = null))
        assertThat(failed.results).isEqualTo(
            ResultsState.Loaded("creeper", titles("Creeper"), continuation = "next", loadMoreFailed = true),
        )
    }
}
