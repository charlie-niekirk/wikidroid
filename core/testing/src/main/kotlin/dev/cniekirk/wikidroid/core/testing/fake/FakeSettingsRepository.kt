package dev.cniekirk.wikidroid.core.testing.fake

import dev.cniekirk.wikidroid.core.data.SettingsRepository
import dev.cniekirk.wikidroid.core.model.Edition
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(
    initial: UserPreferences = UserPreferences(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val preferences: Flow<UserPreferences> get() = state

    /** The latest value, for assertions. */
    val current: UserPreferences get() = state.value

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }

    override suspend fun setDynamicColor(enabled: Boolean) = state.update { it.copy(dynamicColor = enabled) }

    override suspend fun setTextScale(scale: Float) =
        state.update {
            it.copy(textScale = scale.coerceIn(UserPreferences.MIN_TEXT_SCALE, UserPreferences.MAX_TEXT_SCALE))
        }

    override suspend fun setPreferredEdition(edition: Edition) = state.update { it.copy(preferredEdition = edition) }

    override suspend fun setSaveHistory(enabled: Boolean) = state.update { it.copy(saveHistory = enabled) }
}
