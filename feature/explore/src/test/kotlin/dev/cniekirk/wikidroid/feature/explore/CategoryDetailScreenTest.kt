package dev.cniekirk.wikidroid.feature.explore

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Category
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class CategoryDetailScreenTest : ComposeTest() {
    private val actions = mutableListOf<CategoryDetailAction>()

    private val loaded =
        CategoryDetailState(
            title = "Hostile mobs",
            phase = CategoryPhase.Loaded,
            subcategories = persistentListOf(Category("Undead mobs"), Category("Nether mobs")),
            pages =
                persistentListOf(
                    ArticleSummary(title = "Creeper", description = "Explodes near players."),
                    ArticleSummary(title = "Zombie"),
                ),
        )

    private fun setScreen(state: CategoryDetailState) {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                CategoryDetailScreen(state = state, onAction = { actions += it })
            }
        }
    }

    @Test
    fun showsTheCategoryTitleAndABackButton() {
        setScreen(loaded)

        composeRule.onNodeWithText("Hostile mobs").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(actions).containsExactly(CategoryDetailAction.Back)
    }

    @Test
    fun showsASpinnerWhileTheFirstBatchLoads() {
        setScreen(CategoryDetailState(title = "Hostile mobs"))

        composeRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
        composeRule.onNodeWithText("Creeper").assertDoesNotExist()
    }

    @Test
    fun aFailedFirstBatchOffersARetry() {
        setScreen(
            CategoryDetailState(
                title = "Hostile mobs",
                phase = CategoryPhase.Failed(DataError.Network(httpCode = 503)),
            ),
        )

        composeRule
            .onNodeWithText(
                "The wiki couldn't be reached (error 503). Try again in a moment.",
            ).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertThat(actions).containsExactly(CategoryDetailAction.Retry)
    }

    @Test
    fun anEmptyCategorySaysSo() {
        setScreen(CategoryDetailState(title = "Empty", phase = CategoryPhase.Loaded))

        composeRule.onNodeWithText("Nothing here yet").assertIsDisplayed()
    }

    @Test
    fun listsThePagesAndSubcategories() {
        setScreen(loaded)

        composeRule.onNodeWithText("Subcategories").assertIsDisplayed()
        composeRule.onNodeWithText("Undead mobs").assertIsDisplayed()
        composeRule.onNodeWithText("Nether mobs").assertIsDisplayed()
        composeRule.onNodeWithText("Creeper").assertIsDisplayed()
        composeRule.onNodeWithText("Explodes near players.").assertIsDisplayed()
        composeRule.onNodeWithText("Zombie").assertIsDisplayed()
    }

    @Test
    fun omitsTheSubcategoryRowWhenThereAreNone() {
        setScreen(loaded.copy(subcategories = persistentListOf()))

        composeRule.onNodeWithText("Subcategories").assertDoesNotExist()
        composeRule.onNodeWithText("Creeper").assertIsDisplayed()
    }

    @Test
    fun aPageOpensItsArticle() {
        setScreen(loaded)

        composeRule.onNodeWithText("Zombie").performClick()

        assertThat(actions).contains(CategoryDetailAction.OpenArticle("Zombie"))
    }

    @Test
    fun aSubcategoryChipOpensThatCategory() {
        setScreen(loaded)

        composeRule.onNodeWithText("Nether mobs").performClick()

        assertThat(actions).contains(CategoryDetailAction.OpenCategory("Nether mobs"))
    }

    @Test
    fun asksForMoreWhenTheEndOfTheListIsInView() {
        setScreen(loaded.copy(continuation = "next"))

        composeRule.waitForIdle()

        assertThat(actions).contains(CategoryDetailAction.LoadMore)
    }

    @Test
    fun doesNotAskForMoreWhenEverythingIsLoaded() {
        setScreen(loaded.copy(continuation = null))

        composeRule.waitForIdle()

        assertThat(actions).isEmpty()
    }

    @Test
    fun doesNotAskForMoreWhileABatchIsLoading() {
        setScreen(loaded.copy(continuation = "next", isLoadingMore = true))
        composeRule.waitForIdle()
        assertThat(actions).isEmpty()
    }

    @Test
    fun showsASpinnerWhileMoreLoads() {
        setScreen(loaded.copy(continuation = "next", isLoadingMore = true))

        composeRule.onNodeWithContentDescription("Loading more").assertIsDisplayed()
    }

    @Test
    fun aFailedBatchOffersARetryAtTheEndOfTheList() {
        setScreen(loaded.copy(continuation = "next", loadMoreFailed = true))

        composeRule.onNodeWithText("Couldn't load more.").assertIsDisplayed()
        assertThat(actions).isEmpty()
        composeRule.onNodeWithText("Try again").performClick()

        assertThat(actions).containsExactly(CategoryDetailAction.LoadMore)
    }
}
