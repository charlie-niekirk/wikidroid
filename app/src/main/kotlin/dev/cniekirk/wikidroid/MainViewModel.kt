package dev.cniekirk.wikidroid

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dev.cniekirk.wikidroid.core.data.SettingsRepository
import dev.cniekirk.wikidroid.core.model.UserPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** [preferences] is `null` until the first read of the settings file completes. */
@Immutable
data class MainState(
    val preferences: UserPreferences? = null,
)

/** Exposes the user's preferences to the activity so the theme (and the splash screen) can follow them. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class MainViewModel(
    settingsRepository: SettingsRepository,
) : ViewModel(),
    OrbitContainerHost<MainState, MainState, Nothing> {
    override val container =
        orbitContainer<MainState, Nothing>(MainState()) {
            settingsRepository.preferences.collect { preferences -> reduce { MainState(preferences) } }
        }
}
