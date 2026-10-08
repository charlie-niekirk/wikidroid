package dev.cniekirk.wikidroid.feature.settings

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.model.Edition
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.model.UserPreferences

/** Something the reader can wipe from Settings. Both ask for confirmation first. */
enum class ClearTarget { History, ArticleCache }

/** A one-line result shown in a snackbar. */
enum class SettingsMessage { HistoryCleared, CacheCleared, ClearFailed }

/**
 * The Settings tab. [preferences] is `null` until the saved values have been read. [pendingClear] is the
 * confirmation dialog being shown, and [message] the result the screen should show once and then acknowledge.
 */
@Immutable
data class SettingsState(
    val preferences: UserPreferences? = null,
    val pendingClear: ClearTarget? = null,
    val message: SettingsMessage? = null,
)

sealed interface SettingsAction {
    data class SetThemeMode(
        val mode: ThemeMode,
    ) : SettingsAction

    data class SetDynamicColor(
        val enabled: Boolean,
    ) : SettingsAction

    data class SetTextScale(
        val scale: Float,
    ) : SettingsAction

    data class SetPreferredEdition(
        val edition: Edition,
    ) : SettingsAction

    data class SetSaveHistory(
        val enabled: Boolean,
    ) : SettingsAction

    /** Asks for confirmation; nothing is deleted until [ConfirmClear]. */
    data class RequestClear(
        val target: ClearTarget,
    ) : SettingsAction

    data object ConfirmClear : SettingsAction

    data object DismissClear : SettingsAction

    /** The screen has shown [SettingsState.message]. */
    data object MessageShown : SettingsAction

    /** Navigation is the route's job; the ViewModel ignores this. */
    data object OpenAbout : SettingsAction
}
