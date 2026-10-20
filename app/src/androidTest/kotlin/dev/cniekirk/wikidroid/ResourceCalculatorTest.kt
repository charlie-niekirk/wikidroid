package dev.cniekirk.wikidroid

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** The calculator with the recipe book bundled in the APK. */
@Suppress("DEPRECATION")
class ResourceCalculatorTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ResetAppStateRule()).around(composeRule)

    @Test
    fun aBeaconNeedsGlassObsidianAndANetherStar() {
        composeRule.openTab("Tools")
        composeRule.clickText("Resource calculator")
        composeRule.waitForText("Nothing to make yet")

        addItem("Beacon", "beacon")

        composeRule.waitForText("Raw materials")
        listOf("Glass", "Obsidian", "Nether Star", "×5", "×3").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun theListIsStillThereAfterVisitingAnotherTab() {
        composeRule.openTab("Tools")
        composeRule.clickText("Resource calculator")
        composeRule.waitForText("Nothing to make yet")
        addItem("Torch", "torch")
        composeRule.waitForText("Raw materials")

        composeRule.openTab("Explore")
        composeRule.waitForText("Latest versions")
        composeRule.openTab("Tools")

        composeRule.waitForText("Raw materials")
        composeRule.onNodeWithText("Coal").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForText("Work out the raw materials and crafting steps for anything you want to make.")
    }

    private fun addItem(
        query: String,
        id: String,
    ) {
        composeRule.onNodeWithText("Add item", useUnmergedTree = true).performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("calculator-picker-field"), UI_TIMEOUT_MILLIS)
        composeRule.onNodeWithTag("calculator-picker-field").performTextInput(query)
        composeRule.waitUntilAtLeastOneExists(hasTestTag("calculator-picker-$id"), UI_TIMEOUT_MILLIS)
        composeRule.onNodeWithTag("calculator-picker-$id").performClick()
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag("calculator-picker-field")).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntilAtLeastOneExists(hasText(query), UI_TIMEOUT_MILLIS)
    }
}
