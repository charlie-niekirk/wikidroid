package dev.cniekirk.wikidroid.core.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PagingTest : ComposeTest() {
    private var loadMoreCalls = 0
    private var itemCount by mutableIntStateOf(0)
    private lateinit var listState: LazyListState

    private fun setList(
        count: Int,
        buffer: Int = 2,
    ) {
        itemCount = count
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                listState = rememberLazyListState()
                PaginationEffect(listState = listState, onLoadMore = { loadMoreCalls++ }, buffer = buffer)
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items((0 until itemCount).toList()) { Text("Item $it") }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun doesNotFireWhileTheEndIsFarAway() {
        setList(count = 100)

        assertThat(loadMoreCalls).isEqualTo(0)
    }

    @Test
    fun firesWhenScrolledNearTheEnd() {
        setList(count = 100)

        composeRule.runOnIdle { runBlocking { listState.scrollToItem(99) } }
        composeRule.waitForIdle()

        assertThat(loadMoreCalls).isEqualTo(1)
    }

    @Test
    fun firesAgainWhenTheListGrowsWhileTheEndIsStillInView() {
        setList(count = 100)
        composeRule.runOnIdle { runBlocking { listState.scrollToItem(99) } }
        composeRule.waitForIdle()
        assertThat(loadMoreCalls).isEqualTo(1)

        composeRule.runOnIdle { itemCount = 200 }
        composeRule.waitForIdle()
        // The list grew, so the end moved away: nothing new to report until it is reached again.
        assertThat(loadMoreCalls).isEqualTo(1)

        composeRule.runOnIdle { runBlocking { listState.scrollToItem(199) } }
        composeRule.waitForIdle()
        assertThat(loadMoreCalls).isEqualTo(2)
    }

    @Test
    fun aShortListReportsTheEndImmediately() {
        setList(count = 3)

        assertThat(loadMoreCalls).isAtLeast(1)
    }

    @Test
    fun anEmptyListReportsNothing() {
        setList(count = 0)

        assertThat(loadMoreCalls).isEqualTo(0)
    }

    @Test
    fun footerShowsASpinnerWhileLoading() {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                LazyColumn { pagingFooter(isLoading = true, hasError = false, onRetry = {}) }
            }
        }

        composeRule.onNodeWithContentDescription("Loading more").assertIsDisplayed()
    }

    @Test
    fun footerOffersARetryAfterAFailure() {
        var retries = 0
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                LazyColumn { pagingFooter(isLoading = false, hasError = true, onRetry = { retries++ }) }
            }
        }

        composeRule.onNodeWithText("Couldn't load more.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertThat(retries).isEqualTo(1)
    }

    @Test
    fun footerIsAbsentWhenThereIsNothingToShow() {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                LazyColumn {
                    items(listOf("Only item")) { Text(it) }
                    pagingFooter(isLoading = false, hasError = false, onRetry = {})
                }
            }
        }

        composeRule.onNodeWithText("Only item").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Loading more").assertDoesNotExist()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
    }
}
