package dev.cniekirk.wikidroid.core.datastore

import androidx.datastore.core.DataStore
import dev.cniekirk.wikidroid.core.model.UserPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface PreferencesDataSource {
    val userPreferences: Flow<UserPreferences>

    /** Most recent first, at most [MAX_RECENT_SEARCHES]. */
    val recentSearches: Flow<List<String>>

    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences)

    /** Moves an existing entry (compared ignoring case) to the front. Blank queries are ignored. */
    suspend fun addRecentSearch(query: String)

    suspend fun removeRecentSearch(query: String)

    suspend fun clearRecentSearches()

    companion object {
        const val MAX_RECENT_SEARCHES = 10
    }
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DataStorePreferencesDataSource(
    private val dataStore: DataStore<StoredUserData>,
) : PreferencesDataSource {
    override val userPreferences: Flow<UserPreferences> =
        dataStore.data.map { it.preferences.sanitized() }.distinctUntilChanged()

    override val recentSearches: Flow<List<String>> =
        dataStore.data.map { it.recentSearches }.distinctUntilChanged()

    override suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        dataStore.updateData { it.copy(preferences = transform(it.preferences).sanitized()) }
    }

    override suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        dataStore.updateData { data ->
            val others = data.recentSearches.filterNot { it.equals(trimmed, ignoreCase = true) }
            data.copy(recentSearches = (listOf(trimmed) + others).take(PreferencesDataSource.MAX_RECENT_SEARCHES))
        }
    }

    override suspend fun removeRecentSearch(query: String) {
        dataStore.updateData { data ->
            data.copy(recentSearches = data.recentSearches.filterNot { it.equals(query.trim(), ignoreCase = true) })
        }
    }

    override suspend fun clearRecentSearches() {
        dataStore.updateData { it.copy(recentSearches = emptyList()) }
    }
}

/** Keeps a hand-edited or out-of-range text scale from reaching the UI. */
private fun UserPreferences.sanitized(): UserPreferences =
    copy(textScale = textScale.coerceIn(UserPreferences.MIN_TEXT_SCALE, UserPreferences.MAX_TEXT_SCALE))
