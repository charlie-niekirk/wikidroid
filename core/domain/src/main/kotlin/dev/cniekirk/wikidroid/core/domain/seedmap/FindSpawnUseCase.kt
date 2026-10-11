package dev.cniekirk.wikidroid.core.domain.seedmap

import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.zacsweers.metro.Inject

/** The world spawn of [MapWorld], or null in the Nether and End (which have none) or if the engine failed. */
@Inject
class FindSpawnUseCase(
    private val engine: SeedMapEngine,
) {
    suspend operator fun invoke(world: MapWorld): BlockPos? =
        if (world.dimension == Dimension.OVERWORLD) attempt { engine.spawn(world) } else null
}
