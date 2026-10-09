package dev.cniekirk.wikidroid.core.testing.fake

import dev.cniekirk.wikidroid.core.data.SeedRepository
import dev.cniekirk.wikidroid.core.model.SavedSeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Behaves like the real store: a seed and version is saved once, the newest save comes first. */
class FakeSeedRepository(
    initial: List<SavedSeed> = emptyList(),
) : SeedRepository {
    private val state = MutableStateFlow(initial)

    override val savedSeeds: Flow<List<SavedSeed>> get() = state

    /** The latest list, for assertions. */
    val current: List<SavedSeed> get() = state.value

    override suspend fun saveSeed(seed: SavedSeed) {
        state.update { list -> listOf(seed) + list.filterNot { it.seed == seed.seed && it.version == seed.version } }
    }

    override suspend fun removeSeed(seed: SavedSeed) {
        state.update { list -> list.filterNot { it.seed == seed.seed && it.version == seed.version } }
    }
}
