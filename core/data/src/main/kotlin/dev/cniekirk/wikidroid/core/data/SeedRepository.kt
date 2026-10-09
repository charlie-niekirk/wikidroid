package dev.cniekirk.wikidroid.core.data

import dev.cniekirk.wikidroid.core.datastore.PreferencesDataSource
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow

interface SeedRepository {
    /** The seeds the player saved for the seed map, most recently saved first. */
    val savedSeeds: Flow<List<SavedSeed>>

    /** Saves [seed]; saving a seed and version that is already saved updates its label and moves it to the front. */
    suspend fun saveSeed(seed: SavedSeed)

    /** Removes the saved entry for [seed]'s seed and version, whatever its label. */
    suspend fun removeSeed(seed: SavedSeed)
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class PreferencesSeedRepository(
    private val preferences: PreferencesDataSource,
) : SeedRepository {
    override val savedSeeds: Flow<List<SavedSeed>> get() = preferences.savedSeeds

    override suspend fun saveSeed(seed: SavedSeed) = preferences.saveSeed(seed)

    override suspend fun removeSeed(seed: SavedSeed) = preferences.removeSeed(seed)
}
