package dev.cniekirk.wikidroid.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.navigation.ArticleKey
import dev.cniekirk.wikidroid.core.navigation.CategoryKey
import dev.cniekirk.wikidroid.core.navigation.EntryProviderInstaller
import dev.cniekirk.wikidroid.core.navigation.ExploreKey
import dev.cniekirk.wikidroid.core.navigation.Navigator
import dev.cniekirk.wikidroid.core.navigation.SearchKey
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test

class AppShellTest : ComposeTest() {
    private val navigator = Navigator()

    private fun setShell(installers: ImmutableSet<EntryProviderInstaller> = persistentSetOf()) {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                AppShell(navigator = navigator, installers = installers)
            }
        }
    }

    private fun ComposeContentTestRule.tab(label: String) = onNode(hasText(label) and hasClickAction())

    private fun ComposeContentTestRule.assertScreenShows(text: String) =
        onNodeWithTag(PLACEHOLDER_TAG).assert(hasAnyDescendant(hasText(text) and hasNoClickAction()))

    @Test
    fun showsTheFourTabsWithExploreSelected() {
        setShell()

        listOf("Explore", "Search", "Library", "Settings").forEach { composeRule.tab(it).assertIsDisplayed() }
        composeRule.tab("Explore").assertIsSelected()
        composeRule.assertScreenShows("Explore")
    }

    @Test
    fun tappingATabShowsItsScreen() {
        setShell()

        composeRule.tab("Library").performClick()

        composeRule.tab("Library").assertIsSelected()
        composeRule.assertScreenShows("Library")
    }

    @Test
    fun eachTabKeepsItsOwnBackStack() {
        setShell()
        composeRule.runOnIdle { navigator.navigate(ArticleKey("Diamond")) }
        composeRule.assertScreenShows("Article: Diamond")

        composeRule.tab("Search").performClick()
        composeRule.runOnIdle { navigator.navigate(CategoryKey("Mobs")) }
        composeRule.assertScreenShows("Category: Mobs")

        composeRule.tab("Explore").performClick()
        composeRule.assertScreenShows("Article: Diamond")

        composeRule.tab("Search").performClick()
        composeRule.assertScreenShows("Category: Mobs")
    }

    @Test
    fun reselectingTheCurrentTabReturnsToItsRoot() {
        setShell()
        composeRule.runOnIdle { navigator.navigate(ArticleKey("Diamond")) }
        composeRule.assertScreenShows("Article: Diamond")

        composeRule.tab("Explore").performClick()

        composeRule.assertScreenShows("Explore")
    }

    @Test
    fun aFeatureEntryReplacesThePlaceholder() {
        val installer =
            EntryProviderInstaller {
                entry<SearchKey> { Text("Real search screen") }
            }
        setShell(installers = persistentSetOf(installer))

        composeRule.tab("Search").performClick()

        composeRule.onNodeWithText("Real search screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag(PLACEHOLDER_TAG).assertCountEquals(0)
    }

    @Test
    fun keysWithoutAnEntryStillFallBackToThePlaceholder() {
        val installer =
            EntryProviderInstaller {
                entry<ExploreKey> { Text("Real explore screen") }
            }
        setShell(installers = persistentSetOf(installer))
        composeRule.onNodeWithText("Real explore screen").assertIsDisplayed()

        composeRule.runOnIdle { navigator.navigate(ArticleKey("Creeper")) }

        composeRule.assertScreenShows("Article: Creeper")
    }
}
