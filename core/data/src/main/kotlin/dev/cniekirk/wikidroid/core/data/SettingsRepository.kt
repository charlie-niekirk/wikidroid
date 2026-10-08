package dev.cniekirk.wikidroid.core.data

import dev.cniekirk.wikidroid.core.datastore.PreferencesDataSource
import dev.cniekirk.wikidroid.core.model.Edition
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.model.UserPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Emits the current preferences first, then every change. */
    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    /** Values outside `UserPreferences.MIN_TEXT_SCALE..MAX_TEXT_SCALE` are clamped. */
    suspend fun setTextScale(scale: Float)

    suspend fun setPreferredEdition(edition: Edition)

    suspend fun setSaveHistory(enabled: Boolean)
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class DataStoreSettingsRepository(
    private val dataSource: PreferencesDataSource,
) : SettingsRepository {
    override val preferences: Flow<UserPreferences> get() = dataSource.userPreferences

    override suspend fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    override suspend fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    override suspend fun setTextScale(scale: Float) = update { it.copy(textScale = scale) }

    override suspend fun setPreferredEdition(edition: Edition) = update { it.copy(preferredEdition = edition) }

    override suspend fun setSaveHistory(enabled: Boolean) = update { it.copy(saveHistory = enabled) }

    private suspend fun update(transform: (UserPreferences) -> UserPreferences) =
        dataSource.updatePreferences(transform)
}
