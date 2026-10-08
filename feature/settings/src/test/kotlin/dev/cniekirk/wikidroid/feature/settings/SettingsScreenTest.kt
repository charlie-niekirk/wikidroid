package dev.cniekirk.wikidroid.feature.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.Edition
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.model.UserPreferences
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import org.junit.Test

class SettingsScreenTest : ComposeTest() {
    private val actions = mutableListOf<SettingsAction>()

    private fun setScreen(
        state: SettingsState = SettingsState(preferences = UserPreferences()),
        dynamicColorSupported: Boolean = true,
    ) {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                SettingsScreen(
                    state = state,
                    onAction = { actions += it },
                    dynamicColorSupported = dynamicColorSupported,
                )
            }
        }
    }

    private fun scrollTo(text: String) {
        composeRule.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasText(text))
    }

    // region content

    @Test
    fun showsASpinnerUntilThePreferencesAreRead() {
        setScreen(SettingsState())

        composeRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
        composeRule.onNodeWithText("Theme").assertDoesNotExist()
    }

    @Test
    fun marksTheSavedThemeAsSelected() {
        setScreen(SettingsState(preferences = UserPreferences(themeMode = ThemeMode.Dark)))

        composeRule.onNodeWithText("Dark").assertIsSelected()
        composeRule.onNodeWithText("Light").assertIsNotSelected()
        composeRule.onNodeWithText("System").assertIsNotSelected()
    }

    @Test
    fun marksTheSavedEditionAsSelected() {
        setScreen(SettingsState(preferences = UserPreferences(preferredEdition = Edition.Bedrock)))

        scrollTo("Bedrock")
        composeRule.onNodeWithText("Bedrock").assertIsSelected()
        composeRule.onNodeWithText("Java").assertIsNotSelected()
    }

    @Test
    fun showsTheSavedSwitches() {
        setScreen(SettingsState(preferences = UserPreferences(dynamicColor = false, saveHistory = true)))

        composeRule.onNodeWithText("Dynamic colour").assertIsOff()
        scrollTo("Save reading history")
        composeRule.onNodeWithText("Save reading history").assertIsOn()
    }

    @Test
    fun showsTheTextSizeAsAPercentage() {
        setScreen(SettingsState(preferences = UserPreferences(textScale = 1.2f)))

        scrollTo("120%")
        composeRule.onNodeWithText("120%").assertIsDisplayed()
    }

    @Test
    fun dynamicColourIsOfferedOnlyWhereItIsSupported() {
        setScreen(dynamicColorSupported = false)

        composeRule.onNodeWithText("Dynamic colour").assertDoesNotExist()
    }

    // endregion

    // region changing preferences

    @Test
    fun choosingAThemeReportsIt() {
        setScreen()

        composeRule.onNodeWithText("Light").performClick()

        assertThat(actions).containsExactly(SettingsAction.SetThemeMode(ThemeMode.Light))
    }

    @Test
    fun theWholeSwitchRowToggles() {
        setScreen(SettingsState(preferences = UserPreferences(dynamicColor = true)))

        composeRule.onNodeWithText("Dynamic colour").performClick()

        assertThat(actions).containsExactly(SettingsAction.SetDynamicColor(false))
    }

    @Test
    fun theHistorySwitchToggles() {
        setScreen(SettingsState(preferences = UserPreferences(saveHistory = true)))

        scrollTo("Save reading history")
        composeRule.onNodeWithText("Save reading history").performClick()

        assertThat(actions).containsExactly(SettingsAction.SetSaveHistory(false))
    }

    @Test
    fun choosingAnEditionReportsIt() {
        setScreen()

        scrollTo("Bedrock")
        composeRule.onNodeWithText("Bedrock").performClick()

        assertThat(actions).containsExactly(SettingsAction.SetPreferredEdition(Edition.Bedrock))
    }

    @Test
    fun movingTheSliderPreviewsTheSizeAndSavesOnRelease() {
        setScreen()
        scrollTo("100%")

        composeRule
            .onNodeWithTag(
                TEXT_SIZE_SLIDER_TAG,
            ).performSemanticsAction(SemanticsActions.SetProgress) { it(1.3f) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("130%").assertIsDisplayed()
        val saved = actions.filterIsInstance<SettingsAction.SetTextScale>().single()
        assertThat(saved.scale).isWithin(0.001f).of(1.3f)
    }

    // endregion

    // region clearing and about

    @Test
    fun theClearRowsAskForConfirmation() {
        setScreen()
        scrollTo("Clear history")

        composeRule.onNodeWithText("Clear history").performClick()
        scrollTo("Clear article cache")
        composeRule.onNodeWithText("Clear article cache").performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.RequestClear(ClearTarget.History),
                SettingsAction.RequestClear(ClearTarget.ArticleCache),
            ).inOrder()
    }

    @Test
    fun theHistoryDialogExplainsAndConfirms() {
        setScreen(SettingsState(preferences = UserPreferences(), pendingClear = ClearTarget.History))

        composeRule.onNodeWithText("Clear history?").assertIsDisplayed()
        composeRule.onNodeWithText("Clear").performClick()

        assertThat(actions).containsExactly(SettingsAction.ConfirmClear)
    }

    @Test
    fun theCacheDialogSaysBookmarksAreKept() {
        setScreen(SettingsState(preferences = UserPreferences(), pendingClear = ClearTarget.ArticleCache))

        composeRule.onNodeWithText("Clear article cache?").assertIsDisplayed()
        composeRule.onNodeWithText("Downloaded articles will be removed", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(actions).containsExactly(SettingsAction.DismissClear)
    }

    @Test
    fun noDialogUnlessAsked() {
        setScreen()

        composeRule.onNodeWithText("Clear history?").assertDoesNotExist()
        composeRule.onNodeWithText("Clear article cache?").assertDoesNotExist()
    }

    @Test
    fun showsTheResultOfClearingAndThenAcknowledgesIt() {
        setScreen(SettingsState(preferences = UserPreferences(), message = SettingsMessage.CacheCleared))

        composeRule.onNodeWithText("Article cache cleared").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()

        assertThat(actions).containsExactly(SettingsAction.MessageShown)
    }

    @Test
    fun wordsEachResultDifferently() {
        setScreen(SettingsState(preferences = UserPreferences(), message = SettingsMessage.ClearFailed))

        composeRule.onNodeWithText("Couldn't clear that. Try again.").assertIsDisplayed()
    }

    @Test
    fun theAboutRowOpensAbout() {
        setScreen()
        scrollTo("About WikiDroid")

        composeRule.onNodeWithText("About WikiDroid").performClick()

        assertThat(actions).containsExactly(SettingsAction.OpenAbout)
    }

    // endregion
}
