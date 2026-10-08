package dev.cniekirk.wikidroid.core.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import org.junit.Test

class NavigatorSaverTest : ComposeTest() {
    @Test
    fun rememberNavigator_survivesStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        lateinit var navigator: Navigator
        restorationTester.setContent {
            navigator = rememberNavigator()
            Text(navigator.currentKey.toString())
        }
        composeRule.runOnIdle {
            navigator.navigate(CategoryKey("Hostile mobs"))
            navigator.navigate(ArticleKey("Creeper", anchor = "Behavior"))
            navigator.switchTab(SettingsKey)
        }

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle {
            assertThat(navigator.currentTab).isEqualTo(SettingsKey)
            navigator.switchTab(ExploreKey)
            assertThat(navigator.backStack)
                .containsExactly(ExploreKey, CategoryKey("Hostile mobs"), ArticleKey("Creeper", "Behavior"))
                .inOrder()
        }
    }

    @Test
    fun theAboutScreenAboveSettingsSurvivesStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        lateinit var navigator: Navigator
        restorationTester.setContent {
            navigator = rememberNavigator()
            Text(navigator.currentKey.toString())
        }
        composeRule.runOnIdle {
            navigator.switchTab(SettingsKey)
            navigator.navigate(AboutKey)
        }

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle {
            assertThat(navigator.currentTab).isEqualTo(SettingsKey)
            assertThat(navigator.backStack).containsExactly(SettingsKey, AboutKey).inOrder()
        }
    }
}
