package dev.cniekirk.wikidroid.core.domain.seedmap

import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.cniekirk.wikidroid.core.seedmap.StructurePos
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The structures of [types] in [area], searched one type at a time in parallel. A type the engine fails on
 * contributes nothing; the others are still returned.
 */
@Inject
class FindStructuresUseCase(
    private val engine: SeedMapEngine,
) {
    suspend operator fun invoke(
        world: MapWorld,
        types: Set<StructureType>,
        area: BlockArea,
    ): List<StructurePos> =
        coroutineScope {
            types
                .map { type -> async { attempt { engine.structuresIn(world, type, area) }.orEmpty() } }
                .awaitAll()
                .flatten()
        }
}
