package dev.cniekirk.wikidroid.core.testing

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import org.junit.Test

class RobolectricBaseTest : ComposeTest() {
    @Test
    fun composeRule_rendersContentOnTheJvm() {
        composeRule.setContent { BasicText("Hello WikiDroid") }

        composeRule.onNodeWithText("Hello WikiDroid").assertIsDisplayed()
    }
}
