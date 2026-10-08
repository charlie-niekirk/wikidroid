package dev.cniekirk.wikidroid

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.model.UserPreferences
import dev.cniekirk.wikidroid.core.testing.MainDispatcherRule
import dev.cniekirk.wikidroid.core.testing.fake.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Orbit runs intents off the test dispatcher, so the first states may still be the empty one. */
    private suspend fun ReceiveTurbine<MainState>.awaitPreferences(): UserPreferences {
        while (true) awaitItem().preferences?.let { return it }
    }

    @Test
    fun exposesTheStoredPreferences() =
        runTest {
            val repository = FakeSettingsRepository(UserPreferences(themeMode = ThemeMode.Dark, dynamicColor = false))
            val viewModel = MainViewModel(repository)

            viewModel.container.stateFlow.test {
                val preferences = awaitPreferences()

                assertThat(preferences.themeMode).isEqualTo(ThemeMode.Dark)
                assertThat(preferences.dynamicColor).isFalse()
            }
        }

    @Test
    fun followsChangesMadeInSettings() =
        runTest {
            val repository = FakeSettingsRepository()
            val viewModel = MainViewModel(repository)

            viewModel.container.stateFlow.test {
                assertThat(awaitPreferences().themeMode).isEqualTo(ThemeMode.System)

                repository.setThemeMode(ThemeMode.Light)

                assertThat(awaitPreferences().themeMode).isEqualTo(ThemeMode.Light)
            }
        }

    @Test
    fun hasNoPreferencesUntilTheyAreRead() {
        assertThat(MainState().preferences).isNull()
    }
}
