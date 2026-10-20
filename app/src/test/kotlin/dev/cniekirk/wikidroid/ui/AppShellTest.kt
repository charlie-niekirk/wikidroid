package dev.cniekirk.wikidroid.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.navigation.ArticleKey
import dev.cniekirk.wikidroid.core.navigation.CategoryKey
import dev.cniekirk.wikidroid.core.navigation.EntryProviderInstaller
import dev.cniekirk.wikidroid.core.navigation.ExploreKey
import dev.cniekirk.wikidroid.core.navigation.Navigator
import dev.cniekirk.wikidroid.core.navigation.SearchKey
import dev.cniekirk.wikidroid.core.navigation.SeedMapKey
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import dev.cniekirk.wikidroid.core.ui.pane.WikiPanes
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test
import org.robolectric.annotation.Config

class AppShellTest : ComposeTest() {
    private val navigator = Navigator()
    private lateinit var backDispatcher: OnBackPressedDispatcher

    private fun setShell(
        installers: ImmutableSet<EntryProviderInstaller> = persistentSetOf(),
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        composeRule.setContent {
            backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                WikiDroidTheme(dynamicColor = false) {
                    AppShell(navigator = navigator, installers = installers)
                }
            }
        }
    }

    private fun ComposeContentTestRule.tab(label: String) = onNode(hasText(label) and hasClickAction())

    private fun ComposeContentTestRule.assertScreenShows(text: String) =
        onNodeWithTag(PLACEHOLDER_TAG).assert(hasAnyDescendant(hasText(text) and hasNoClickAction()))

    @Test
    fun showsTheFiveTabsWithExploreSelected() {
        setShell()

        listOf("Explore", "Search", "Seed map", "Tools", "Settings").forEach { composeRule.tab(it).assertIsDisplayed() }
        composeRule.tab("Explore").assertIsSelected()
        composeRule.assertScreenShows("Explore")
    }

    @Test
    fun tappingATabShowsItsScreen() {
        setShell()

        composeRule.tab("Tools").performClick()

        composeRule.tab("Tools").assertIsSelected()
        composeRule.assertScreenShows("Tools")
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

    @Test
    @Config(qualifiers = WIDE_WINDOW)
    fun aWideWindowShowsAnArticleBesideTheListThatOpenedIt() {
        val installer =
            EntryProviderInstaller {
                entry<ExploreKey>(metadata = WikiPanes.list()) { Text("Real explore screen") }
            }
        setShell(installers = persistentSetOf(installer))

        composeRule.runOnIdle { navigator.navigate(ArticleKey("Diamond")) }

        composeRule.onNodeWithText("Real explore screen").assertIsDisplayed()
        composeRule.assertScreenShows("Article: Diamond")
    }

    @Test
    @Config(qualifiers = WIDE_WINDOW)
    fun aWideWindowKeepsTheSeedMapAndItsArticlesFullScreen() {
        val installer =
            EntryProviderInstaller {
                entry<SeedMapKey> {
                    Box(Modifier.fillMaxSize().testTag(SEED_MAP_TAG))
                }
            }
        setShell(installers = persistentSetOf(installer))

        composeRule.tab("Seed map").performClick()
        composeRule.onNodeWithText("Select a page").assertDoesNotExist()
        val mapWidth = composeRule.onNodeWithTag(SEED_MAP_TAG).getBoundsInRoot().let { it.right - it.left }

        composeRule.runOnIdle { navigator.navigate(ArticleKey("Diamond")) }

        composeRule.assertScreenShows("Article: Diamond")
        composeRule.onNodeWithTag(SEED_MAP_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(PLACEHOLDER_TAG).assertWidthIsEqualTo(mapWidth)
    }

    @Test
    fun aTabKeepsItsViewModelsAndSavedStateWhileAnotherTabIsShown() {
        setShell(installers = persistentSetOf(countingCategoryEntry()))
        composeRule.runOnIdle { navigator.navigate(CategoryKey("Mobs")) }
        composeRule.onNodeWithText("Mobs #1, taps 0").performClick()
        composeRule.onNodeWithText("Mobs #1, taps 1").assertIsDisplayed()

        composeRule.tab("Tools").performClick()
        composeRule.tab("Explore").performClick()

        composeRule.onNodeWithText("Mobs #1, taps 1").assertIsDisplayed()
        composeRule.runOnIdle {
            assertThat(viewModels).hasSize(1)
            assertThat(viewModels.single().cleared).isFalse()
        }
    }

    @Test
    fun poppingAnEntryClearsItsViewModel() {
        setShell(installers = persistentSetOf(countingCategoryEntry()))
        composeRule.runOnIdle { navigator.navigate(CategoryKey("Mobs")) }
        composeRule.onNodeWithText("Mobs #1, taps 0").assertIsDisplayed()

        composeRule.runOnIdle { navigator.goBack() }

        composeRule.assertScreenShows("Explore")
        composeRule.runOnIdle { assertThat(viewModels.single().cleared).isTrue() }
    }

    @Test
    fun theSameDestinationOnTwoTabsHasSeparateState() {
        setShell(installers = persistentSetOf(countingCategoryEntry()))
        composeRule.runOnIdle { navigator.navigate(CategoryKey("Mobs")) }
        composeRule.onNodeWithText("Mobs #1, taps 0").performClick()

        composeRule.tab("Search").performClick()
        composeRule.runOnIdle { navigator.navigate(CategoryKey("Mobs")) }
        composeRule.onNodeWithText("Mobs #2, taps 0").assertIsDisplayed()

        composeRule.tab("Explore").performClick()
        composeRule.onNodeWithText("Mobs #1, taps 1").assertIsDisplayed()
    }

    @Test
    fun backFromAnotherTabsRootReturnsToTheFirstTab() {
        setShell()
        composeRule.tab("Tools").performClick()

        composeRule.runOnIdle { backDispatcher.onBackPressed() }

        composeRule.tab("Explore").assertIsSelected()
        composeRule.assertScreenShows("Explore")
    }

    @Test
    fun aPushSlidesTheNewScreenInFromTheEnd() {
        setShell(installers = persistentSetOf(taggedEntries()))
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnUiThread { navigator.navigate(CategoryKey("Mobs")) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(HALFWAY_MILLIS)

        assertThat(bounds(CATEGORY_TAG).left).isGreaterThan(0f)
        assertThat(bounds(EXPLORE_TAG).right).isLessThan(displayWidth())
    }

    @Test
    fun aPopSlidesTheScreenOutTowardsTheEnd() {
        setShell(installers = persistentSetOf(taggedEntries()))
        composeRule.runOnIdle { navigator.navigate(CategoryKey("Mobs")) }
        composeRule.onNodeWithTag(CATEGORY_TAG).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnUiThread { navigator.goBack() }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(HALFWAY_MILLIS)

        assertThat(bounds(CATEGORY_TAG).left).isGreaterThan(0f)
        assertThat(bounds(EXPLORE_TAG).right).isLessThan(displayWidth())
    }

    @Test
    fun aPushInARightToLeftLayoutComesInFromTheLeft() {
        setShell(installers = persistentSetOf(taggedEntries()), layoutDirection = LayoutDirection.Rtl)
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnUiThread { navigator.navigate(CategoryKey("Mobs")) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(HALFWAY_MILLIS)

        assertThat(bounds(CATEGORY_TAG).right).isLessThan(displayWidth())
        assertThat(bounds(EXPLORE_TAG).left).isGreaterThan(0f)
    }

    @Test
    fun switchingTabsDoesNotAnimate() {
        setShell(installers = persistentSetOf(taggedEntries()))
        composeRule.mainClock.autoAdvance = false

        composeRule.tab("Search").performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()

        assertThat(bounds(SEARCH_TAG).left).isEqualTo(0f)
        composeRule.onNodeWithTag(EXPLORE_TAG).assertDoesNotExist()
    }

    // Bounds in root are clipped to the display, so a screen that has slid part way out shows as narrower, not offset.
    private fun bounds(tag: String): Rect =
        composeRule.onNodeWithTag(tag).getBoundsInRoot().let {
            Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
        }

    private fun displayWidth(): Float =
        composeRule
            .onRoot()
            .getBoundsInRoot()
            .right.value

    /** Full-size screens for Explore, Search and categories, tagged so a test can find each while both are shown. */
    private fun taggedEntries() =
        EntryProviderInstaller {
            entry<ExploreKey> { Box(Modifier.fillMaxSize().testTag(EXPLORE_TAG)) }
            entry<SearchKey> { Box(Modifier.fillMaxSize().testTag(SEARCH_TAG)) }
            entry<CategoryKey> { Box(Modifier.fillMaxSize().testTag(CATEGORY_TAG)) }
        }

    /** A category screen whose text names its view model (numbered in creation order) and a saved tap count. */
    private fun countingCategoryEntry() =
        EntryProviderInstaller {
            entry<CategoryKey> { key ->
                val viewModel = viewModel { CountingViewModel(viewModels.size + 1).also(viewModels::add) }
                var taps by rememberSaveable { mutableIntStateOf(0) }
                Text(
                    text = "${key.title} #${viewModel.number}, taps $taps",
                    modifier = Modifier.clickable { taps++ },
                )
            }
        }

    private val viewModels = mutableListOf<CountingViewModel>()

    private class CountingViewModel(
        val number: Int,
    ) : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    private companion object {
        const val WIDE_WINDOW = "w1280dp-h800dp"
        const val SEED_MAP_TAG = "seed-map"
        const val EXPLORE_TAG = "explore"
        const val SEARCH_TAG = "search"
        const val CATEGORY_TAG = "category"
        const val HALFWAY_MILLIS = NavTransitions.DURATION_MILLIS / 2L
    }
}
