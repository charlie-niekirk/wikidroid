package dev.cniekirk.wikidroid.core.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import org.junit.Test

class ArticleCardTest : ComposeTest() {
    @Test
    fun showsTitleAndDescription() {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                ArticleCard(
                    article = ArticleSummary(title = "Diamond", description = "A rare mineral."),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Diamond").assertIsDisplayed()
        composeRule.onNodeWithText("A rare mineral.").assertIsDisplayed()
    }

    @Test
    fun showsTheDisplayTitle() {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                ArticleCard(article = ArticleSummary(title = "Commands/give", displayTitle = "/give"), onClick = {})
            }
        }

        composeRule.onNodeWithText("/give").assertIsDisplayed()
        composeRule.onNodeWithText("Commands/give").assertDoesNotExist()
    }

    @Test
    fun clickInvokesCallback() {
        var clicks = 0
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                ArticleCard(article = ArticleSummary(title = "Creeper"), onClick = { clicks++ })
            }
        }

        composeRule.onNodeWithText("Creeper").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun rendersTrailingContent() {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                ArticleCard(
                    article = ArticleSummary(title = "Creeper"),
                    onClick = {},
                    trailingContent = { Text("Offline") },
                )
            }
        }

        composeRule.onNodeWithText("Offline").assertIsDisplayed()
    }
}
