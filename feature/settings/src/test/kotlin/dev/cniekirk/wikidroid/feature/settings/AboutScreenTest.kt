package dev.cniekirk.wikidroid.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import org.junit.Test

class AboutScreenTest : ComposeTest() {
    private val openedUrls = mutableListOf<String>()
    private var backs = 0

    private fun setScreen(versionName: String? = "1.2.3") {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                AboutScreen(versionName = versionName, onOpenUrl = { openedUrls += it }, onNavigateBack = { backs++ })
            }
        }
    }

    private fun scrollTo(text: String) {
        composeRule.onNodeWithTag(ABOUT_LIST_TAG).performScrollToNode(hasText(text))
    }

    @Test
    fun showsTheVersion() {
        setScreen("1.2.3")

        composeRule.onNodeWithText("Version 1.2.3").assertIsDisplayed()
    }

    @Test
    fun omitsTheVersionWhenItCantBeRead() {
        setScreen(versionName = null)

        composeRule.onNodeWithText("Version", substring = true).assertDoesNotExist()
    }

    @Test
    fun statesThatTheAppIsUnofficial() {
        setScreen()

        composeRule.onNodeWithText("Unofficial app").assertIsDisplayed()
        composeRule.onNodeWithText("not affiliated with Mojang or Microsoft", substring = true).assertIsDisplayed()
    }

    @Test
    fun attributesTheWikiContentAndItsLicence() {
        setScreen()

        composeRule
            .onNodeWithText(
                "Creative Commons Attribution-NonCommercial-ShareAlike 3.0",
                substring = true,
            ).assertIsDisplayed()
        composeRule.onNodeWithText("no ads", substring = true).assertIsDisplayed()
    }

    @Test
    fun linksToTheLicenceTheWikiAndTheSource() {
        setScreen()

        composeRule.onNodeWithText("View the licence").performClick()
        composeRule.onNodeWithText("Visit the Minecraft Wiki").performClick()
        scrollTo("WikiDroid on GitHub")
        composeRule.onNodeWithText("WikiDroid on GitHub").performClick()

        assertThat(openedUrls)
            .containsExactly(
                "https://creativecommons.org/licenses/by-nc-sa/3.0/",
                "https://minecraft.wiki",
                "https://github.com/charlie-niekirk/wikidroid",
            ).inOrder()
    }

    @Test
    fun listsEveryLibraryWithItsLicence() {
        setScreen()

        OpenSourceLibraries.forEach { library ->
            scrollTo(library.name)
            composeRule.onNodeWithText(library.name).assertIsDisplayed()
        }
        scrollTo("jsoup")
        composeRule.onNodeWithText("MIT License").assertIsDisplayed()
    }

    @Test
    fun aLibraryOpensItsProjectPage() {
        setScreen()

        scrollTo("Coil")
        composeRule.onNodeWithText("Coil").performClick()

        assertThat(openedUrls).containsExactly("https://github.com/coil-kt/coil")
    }

    @Test
    fun theBackArrowGoesBack() {
        setScreen()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }
}
