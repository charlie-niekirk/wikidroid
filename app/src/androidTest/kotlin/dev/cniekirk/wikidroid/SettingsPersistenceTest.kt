package dev.cniekirk.wikidroid

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@Suppress("DEPRECATION")
class SettingsPersistenceTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ResetAppStateRule()).around(composeRule)

    @Test
    fun theThemeChoiceSurvivesClosingAndReopeningTheApp() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.openTab("Settings")
            composeRule.clickText("Dark")
            composeRule.onNode(hasText("Dark") and isSelectable()).assertIsSelected()
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.openTab("Settings")
            composeRule.waitForText("Appearance")
            composeRule.onNode(hasText("Dark") and isSelectable()).assertIsSelected()
        }
        assertThat(currentPreferences().themeMode).isEqualTo(ThemeMode.Dark)
    }

    @Test
    fun theHistoryToggleIsSavedAndShownInLibrary() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.openTab("Settings")
            composeRule.clickText("Save reading history")
            composeRule.waitUntil(UI_TIMEOUT_MILLIS) { !currentPreferences().saveHistory }

            composeRule.openTab("Library")
            composeRule.clickText("History")
            composeRule.waitForText("History is turned off")
        }
    }

    private fun currentPreferences() =
        runBlocking {
            testApp.graph.settingsRepository.preferences
                .first()
        }
}
