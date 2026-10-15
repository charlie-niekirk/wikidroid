package dev.cniekirk.wikidroid

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@Suppress("DEPRECATION")
class TabNavigationTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ResetAppStateRule()).around(composeRule)

    @Test
    fun exploreLoadsTheLatestVersionsFromTheWiki() {
        composeRule.waitForText("Latest versions")
        composeRule.waitForText("26.3")
        composeRule.tab("Explore").assertIsSelected()
    }

    @Test
    fun everyTabShowsItsOwnScreen() {
        composeRule.openTab("Search")
        composeRule.waitForText("What are you looking for?")
        composeRule.tab("Search").assertIsSelected()

        composeRule.openTab("Seed map")
        composeRule.waitUntilAtLeastOneExists(hasTestTag("seed-map-canvas"), UI_TIMEOUT_MILLIS)
        composeRule.tab("Seed map").assertIsSelected()

        composeRule.openTab("Library")
        composeRule.waitForText("No bookmarks yet")
        composeRule.tab("Library").assertIsSelected()

        composeRule.openTab("Settings")
        composeRule.waitForText("Appearance")
        composeRule.tab("Settings").assertIsSelected()

        composeRule.openTab("Explore")
        composeRule.waitForText("Latest versions")
        composeRule.tab("Explore").assertIsSelected()
    }

    @Test
    fun aVersionRowOpensItsArticle() {
        composeRule.waitForText("26.3")
        composeRule.clickText("26.3")

        composeRule.waitUntilAtLeastOneExists(hasTestTag("article-list"), UI_TIMEOUT_MILLIS)
    }

    @Test
    fun aSearchIsStillThereAfterVisitingAnotherTab() {
        composeRule.openTab("Search")
        composeRule.onNodeWithTag("search-field").performTextInput("Diamond")
        val suggestion = hasText("Diamond") and hasClickAction() and !hasSetTextAction()
        composeRule.waitUntilAtLeastOneExists(suggestion, UI_TIMEOUT_MILLIS)

        composeRule.openTab("Library")
        composeRule.waitForText("No bookmarks yet")
        composeRule.openTab("Search")

        composeRule.onNodeWithTag("search-field").assertTextContains("Diamond")
        composeRule.onNode(suggestion).assertIsDisplayed()
    }
}
