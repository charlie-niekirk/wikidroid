package dev.cniekirk.wikidroid.core.datastore

import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.model.UserPreferences
import kotlinx.serialization.Serializable

/**
 * The single JSON file behind [PreferencesDataSource]: user preferences, recent searches and saved map seeds
 * live together so they share one DataStore. Every field has a default so older files stay readable.
 */
@Serializable
data class StoredUserData(
    val preferences: UserPreferences = UserPreferences(),
    /** Most recent first. */
    val recentSearches: List<String> = emptyList(),
    /** Most recently saved first. */
    val savedSeeds: List<SavedSeed> = emptyList(),
)
