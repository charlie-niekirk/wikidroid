package dev.cniekirk.wikidroid

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** The app's main journey: find an article, read it, save it, and find it again in Library. */
@Suppress("DEPRECATION")
class SearchToLibraryTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ResetAppStateRule()).around(composeRule)

    @Test
    fun searchOpenBookmarkAndFindItInLibrary() {
        composeRule.openTab("Search")
        composeRule.onNodeWithTag("search-field").performTextInput("Diamond")

        // The field holds "Diamond" too, so pick the clickable row that isn't the text field.
        val suggestion = hasText("Diamond") and hasClickAction() and !hasSetTextAction()
        composeRule.waitUntilAtLeastOneExists(suggestion, UI_TIMEOUT_MILLIS)
        composeRule.onNode(suggestion).performClick()

        composeRule.waitUntilAtLeastOneExists(hasTestTag("article-list"), UI_TIMEOUT_MILLIS)
        composeRule.onNodeWithContentDescription("Save for offline reading").performClick()
        composeRule.waitUntilAtLeastOneExists(hasContentDescription("Remove from saved"), UI_TIMEOUT_MILLIS)

        composeRule.openTab("Library")
        composeRule.waitUntilAtLeastOneExists(hasTestTag("library-list"), UI_TIMEOUT_MILLIS)
        composeRule.onNodeWithText("Diamond").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Available offline").assertIsDisplayed()

        val bookmarks =
            runBlocking {
                testApp.graph.libraryRepository.bookmarks
                    .first()
            }
        assertThat(bookmarks.map { it.title }).containsExactly("Diamond")
    }

    @Test
    fun readingAnArticleAddsItToHistory() {
        composeRule.openTab("Search")
        composeRule.onNodeWithTag("search-field").performTextInput("Diamond")
        val suggestion = hasText("Diamond") and hasClickAction() and !hasSetTextAction()
        composeRule.waitUntilAtLeastOneExists(suggestion, UI_TIMEOUT_MILLIS)
        composeRule.onNode(suggestion).performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("article-list"), UI_TIMEOUT_MILLIS)

        composeRule.openTab("Library")
        composeRule.clickText("History")
        composeRule.waitUntilAtLeastOneExists(hasTestTag("library-list"), UI_TIMEOUT_MILLIS)
        composeRule.onNodeWithText("Diamond").assertIsDisplayed()
    }
}
