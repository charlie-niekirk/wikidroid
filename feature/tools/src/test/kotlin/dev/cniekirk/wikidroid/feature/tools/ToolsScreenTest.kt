package dev.cniekirk.wikidroid.feature.tools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import org.junit.Test

class ToolsScreenTest : ComposeTest() {
    private val opened = mutableListOf<Tool>()

    @Test
    fun listsEveryToolAndOpensTheOneTapped() {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                ToolsScreen(onOpenTool = { opened += it })
            }
        }

        composeRule.onNodeWithText("Tools").assertIsDisplayed()
        composeRule.onNodeWithText("Resource calculator").performClick()

        assertThat(opened).containsExactly(Tool.ResourceCalculator)
    }
}
