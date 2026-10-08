package dev.cniekirk.wikidroid.feature.settings

import androidx.lifecycle.ViewModel
import dev.cniekirk.wikidroid.core.common.dataResultOf
import dev.cniekirk.wikidroid.core.common.fold
import dev.cniekirk.wikidroid.core.data.ArticleRepository
import dev.cniekirk.wikidroid.core.data.LibraryRepository
import dev.cniekirk.wikidroid.core.data.SettingsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * Drives the Settings tab. Preference changes go straight to the repository and come back through its flow,
 * so the screen and the rest of the app (theme, article text size) always agree on what is saved.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val articles: ArticleRepository,
) : ViewModel(),
    OrbitContainerHost<SettingsState, SettingsState, Nothing> {
    override val container =
        orbitContainer<SettingsState, Nothing>(SettingsState()) {
            settings.preferences.collect { preferences -> reduce { state.copy(preferences = preferences) } }
        }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SetThemeMode -> intent { settings.setThemeMode(action.mode) }
            is SettingsAction.SetDynamicColor -> intent { settings.setDynamicColor(action.enabled) }
            is SettingsAction.SetTextScale -> intent { settings.setTextScale(action.scale) }
            is SettingsAction.SetPreferredEdition -> intent { settings.setPreferredEdition(action.edition) }
            is SettingsAction.SetSaveHistory -> intent { settings.setSaveHistory(action.enabled) }
            is SettingsAction.RequestClear -> intent { reduce { state.copy(pendingClear = action.target) } }
            SettingsAction.DismissClear -> intent { reduce { state.copy(pendingClear = null) } }
            SettingsAction.ConfirmClear -> confirmClear()
            SettingsAction.MessageShown -> intent { reduce { state.copy(message = null) } }
            SettingsAction.OpenAbout -> Unit
        }
    }

    private fun confirmClear() =
        intent {
            val target = state.pendingClear ?: return@intent
            reduce { state.copy(pendingClear = null) }
            val outcome =
                dataResultOf {
                    when (target) {
                        ClearTarget.History -> library.clearHistory()
                        ClearTarget.ArticleCache -> articles.clearCache()
                    }
                }
            val message =
                outcome.fold(
                    onSuccess = {
                        when (target) {
                            ClearTarget.History -> SettingsMessage.HistoryCleared
                            ClearTarget.ArticleCache -> SettingsMessage.CacheCleared
                        }
                    },
                    onFailure = { SettingsMessage.ClearFailed },
                )
            reduce { state.copy(message = message) }
        }
}
