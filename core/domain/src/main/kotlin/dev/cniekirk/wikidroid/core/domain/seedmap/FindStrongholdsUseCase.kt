package dev.cniekirk.wikidroid.core.domain.seedmap

import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.zacsweers.metro.Inject

/** Every stronghold of [MapWorld], up to [SeedMapEngine.MAX_STRONGHOLDS]; none if the engine failed. */
@Inject
class FindStrongholdsUseCase(
    private val engine: SeedMapEngine,
) {
    suspend operator fun invoke(world: MapWorld): List<BlockPos> =
        attempt { engine.strongholds(world, SeedMapEngine.MAX_STRONGHOLDS) }.orEmpty()
}
