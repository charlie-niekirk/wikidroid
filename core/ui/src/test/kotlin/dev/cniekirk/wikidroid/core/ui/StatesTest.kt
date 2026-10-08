package dev.cniekirk.wikidroid.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import org.junit.Test

class StatesTest : ComposeTest() {
    @Test
    fun loadingState_isAnnouncedAsLoading() {
        composeRule.setContent { WikiDroidTheme(dynamicColor = false) { LoadingState() } }

        composeRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun emptyState_showsTitleAndMessage() {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                EmptyState(title = "No bookmarks", message = "Saved articles appear here.")
            }
        }

        composeRule.onNodeWithText("No bookmarks").assertIsDisplayed()
        composeRule.onNodeWithText("Saved articles appear here.").assertIsDisplayed()
    }

    @Test
    fun errorState_offlineExplainsAndOffersRetry() {
        var retries = 0
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                ErrorState(error = DataError.Network(), onRetry = { retries++ })
            }
        }

        composeRule.onNodeWithText("You appear to be offline. Check your connection and try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertThat(retries).isEqualTo(1)
    }

    @Test
    fun errorState_withoutRetryHasNoButton() {
        composeRule.setContent { WikiDroidTheme(dynamicColor = false) { ErrorState(error = DataError.NotFound) } }

        composeRule.onNodeWithText("That page doesn't exist on the wiki.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun errorState_describesEachFailureKind() {
        val cases =
            listOf(
                DataError.Network(httpCode = 503) to "The wiki couldn't be reached (error 503). Try again in a moment.",
                DataError.Api(code = "badtitle", info = "Invalid title") to
                    "The wiki rejected the request: Invalid title",
                DataError.Api(code = "badtitle", info = null) to "The wiki rejected the request: badtitle",
                DataError.Parse to "The wiki's response couldn't be understood.",
                DataError.Unknown() to "An unexpected error occurred.",
            )
        var error: DataError by mutableStateOf(cases.first().first)
        composeRule.setContent { WikiDroidTheme(dynamicColor = false) { ErrorState(error = error) } }

        cases.forEach { (case, message) ->
            error = case
            composeRule.onNodeWithText(message).assertIsDisplayed()
        }
    }
}
