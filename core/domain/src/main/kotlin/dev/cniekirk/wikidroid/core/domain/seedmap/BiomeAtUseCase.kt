package dev.cniekirk.wikidroid.core.domain.seedmap

import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.zacsweers.metro.Inject

/** The biome id at [BlockPos] in [MapWorld], or null if there is none or the engine failed. */
@Inject
class BiomeAtUseCase(
    private val engine: SeedMapEngine,
) {
    suspend operator fun invoke(
        world: MapWorld,
        pos: BlockPos,
    ): Int? = attempt { engine.biomeAt(world, pos.x, pos.z) }
}
