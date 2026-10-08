package dev.cniekirk.wikidroid.feature.explore

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.LatestVersions
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import org.junit.Test

class ExploreScreenTest : ComposeTest() {
    private val actions = mutableListOf<ExploreAction>()

    private val allVersions =
        LatestVersions(
            java = "26.3",
            javaSnapshot = "26.4 Snapshot 3",
            bedrock = "26.52",
            bedrockPreview = "Preview 26.60.30",
        )

    private fun setScreen(state: ExploreState) {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                ExploreScreen(state = state, onAction = { actions += it })
            }
        }
    }

    @Test
    fun showsTheScreenTitleAndTheSectionHeaders() {
        setScreen(ExploreState())

        composeRule.onNodeWithText("Explore").assertIsDisplayed()
        composeRule.onNodeWithText("Latest versions").assertIsDisplayed()
        composeRule.onNodeWithText("Browse by category").assertIsDisplayed()
    }

    @Test
    fun everyReportedVersionIsListedWithItsEdition() {
        setScreen(ExploreState(versions = VersionsState.Loaded(allVersions)))

        listOf("26.3", "26.4 Snapshot 3", "26.52", "Preview 26.60.30").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithText("Java Edition").assertIsDisplayed()
        composeRule.onNodeWithText("Java Edition snapshot").assertIsDisplayed()
        composeRule.onNodeWithText("Bedrock Edition").assertIsDisplayed()
        composeRule.onNodeWithText("Bedrock Edition preview").assertIsDisplayed()
    }

    @Test
    fun tappingAVersionOpensItsPage() {
        setScreen(ExploreState(versions = VersionsState.Loaded(allVersions)))

        composeRule.onNodeWithText("26.3").performClick()
        composeRule.onNodeWithText("26.4 Snapshot 3").performClick()
        composeRule.onNodeWithText("26.52").performClick()
        composeRule.onNodeWithText("Preview 26.60.30").performClick()

        assertThat(actions)
            .containsExactly(
                ExploreAction.OpenArticle("Java Edition 26.3"),
                ExploreAction.OpenArticle("Java Edition 26.4 Snapshot 3"),
                ExploreAction.OpenArticle("Bedrock Edition 26.52"),
                ExploreAction.OpenArticle("Bedrock Edition Preview 26.60.30"),
            ).inOrder()
    }

    @Test
    fun leavesOutEditionsTheWikiDidNotReport() {
        setScreen(
            ExploreState(
                versions =
                    VersionsState.Loaded(
                        LatestVersions(java = "26.3", javaSnapshot = null, bedrock = null, bedrockPreview = null),
                    ),
            ),
        )

        composeRule.onNodeWithText("Java Edition").assertIsDisplayed()
        composeRule.onNodeWithText("Java Edition snapshot").assertDoesNotExist()
        composeRule.onNodeWithText("Bedrock Edition").assertDoesNotExist()
    }

    @Test
    fun showsNoVersionRowsWhileTheyLoad() {
        setScreen(ExploreState(versions = VersionsState.Loading))

        composeRule.onNodeWithText("Latest versions").assertIsDisplayed()
        composeRule.onNodeWithText("Java Edition").assertDoesNotExist()
    }

    @Test
    fun aVersionsFailureExplainsItself_andRetries() {
        setScreen(ExploreState(versions = VersionsState.Failed(DataError.Network())))

        composeRule.onNodeWithText("Couldn't load the latest versions.").assertIsDisplayed()
        composeRule.onNodeWithText("You appear to be offline. Check your connection and try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertThat(actions).containsExactly(ExploreAction.RetryVersions)
    }

    @Test
    fun theRandomArticleCardOpensARandomArticle() {
        setScreen(ExploreState())

        composeRule.onNodeWithTag(RANDOM_CARD_TAG).assertIsEnabled().performClick()

        assertThat(actions).containsExactly(ExploreAction.OpenRandomArticle)
    }

    @Test
    fun theRandomArticleCardIsDisabledWhileItLoads() {
        setScreen(ExploreState(isLoadingRandom = true))

        composeRule.onNodeWithTag(RANDOM_CARD_TAG).assertIsNotEnabled()
    }

    @Test
    fun tappingACategoryOpensIt() {
        setScreen(ExploreState())

        composeRule.onNodeWithText("Blocks").performClick()

        assertThat(actions).containsExactly(ExploreAction.OpenCategory("Blocks"))
    }

    @Test
    fun everyCuratedCategoryCanBeReachedByScrolling() {
        setScreen(ExploreState())

        ExploreCategories.forEach { category ->
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(category.displayName))
            composeRule.onNodeWithText(category.displayName).assertIsDisplayed()
        }
    }

    @Test
    fun aLateCategoryOpensWithItsWikiName() {
        setScreen(ExploreState())

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Generated structures"))
        composeRule.onNodeWithText("Generated structures").performClick()

        assertThat(actions).containsExactly(ExploreAction.OpenCategory("Generated structures"))
    }
}
