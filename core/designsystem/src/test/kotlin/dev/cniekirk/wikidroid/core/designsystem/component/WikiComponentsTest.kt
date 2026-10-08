package dev.cniekirk.wikidroid.core.designsystem.component

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import org.junit.Test

class WikiComponentsTest : ComposeTest() {
    @Test
    fun wikiIcon_exposesItsContentDescription() {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                WikiIcon(icon = WikiIcons.Search, contentDescription = "Search")
            }
        }

        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test
    fun wikiIconButton_invokesOnClick() {
        var clicks = 0
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                WikiIconButton(icon = WikiIcons.Share, contentDescription = "Share", onClick = { clicks++ })
            }
        }

        composeRule.onNodeWithContentDescription("Share").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun wikiIconButton_disabledIsNotClickable() {
        var clicks = 0
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                WikiIconButton(
                    icon = WikiIcons.Share,
                    contentDescription = "Share",
                    onClick = { clicks++ },
                    enabled = false,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Share").assertIsNotEnabled()
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun topAppBar_showsTitleAndBackNavigation() {
        var backs = 0
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                WikiTopAppBar(
                    title = "Diamond",
                    onNavigateBack = { backs++ },
                    navigateBackDescription = "Back",
                )
            }
        }

        composeRule.onNodeWithText("Diamond").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertContentDescriptionEquals("Back").performClick()
        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun topAppBar_withoutBackHandlerHasNoBackButton() {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                WikiTopAppBar(title = "Explore", navigateBackDescription = "Back")
            }
        }

        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun topAppBar_rendersActions() {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                WikiTopAppBar(
                    title = "Diamond",
                    actions = {
                        WikiIconButton(icon = WikiIcons.Settings, contentDescription = "Settings", onClick = {})
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }
}
