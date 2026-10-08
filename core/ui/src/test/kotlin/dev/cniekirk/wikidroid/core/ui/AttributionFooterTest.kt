package dev.cniekirk.wikidroid.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import org.junit.Test

class AttributionFooterTest : ComposeTest() {
    private val opened = mutableListOf<String>()

    private fun show(pageUrl: String?) {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                AttributionFooter(onOpenUrl = { opened += it }, pageUrl = pageUrl)
            }
        }
    }

    @Test
    fun creditsTheWikiAndItsLicence() {
        show("https://minecraft.wiki/w/Diamond")

        composeRule
            .onNodeWithText("Content from the Minecraft Wiki, available under CC BY-NC-SA 3.0.")
            .assertIsDisplayed()
    }

    @Test
    fun linksToThePageItsHistoryAndTheLicence() {
        show("https://minecraft.wiki/w/Diamond")

        composeRule.onNodeWithText("View page").performClick()
        composeRule.onNodeWithText("View history").performClick()
        composeRule.onNodeWithText("License").performClick()

        assertThat(opened)
            .containsExactly(
                "https://minecraft.wiki/w/Diamond",
                "https://minecraft.wiki/w/Diamond?action=history",
                LICENSE_URL,
            ).inOrder()
    }

    @Test
    fun withoutAPageUrlOnlyTheLicenceLinkRemains() {
        show(pageUrl = null)

        composeRule.onNodeWithText("View page").assertDoesNotExist()
        composeRule.onNodeWithText("View history").assertDoesNotExist()
        composeRule.onNodeWithText("License").assertIsDisplayed()
    }

    @Test
    fun historyUrlAppendsToExistingQueries() {
        assertThat(
            historyUrl("https://minecraft.wiki/w/Diamond"),
        ).isEqualTo("https://minecraft.wiki/w/Diamond?action=history")
        assertThat(historyUrl("https://minecraft.wiki/index.php?title=Diamond"))
            .isEqualTo("https://minecraft.wiki/index.php?title=Diamond&action=history")
    }
}
