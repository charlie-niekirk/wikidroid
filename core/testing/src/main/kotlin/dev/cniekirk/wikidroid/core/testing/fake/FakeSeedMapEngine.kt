package dev.cniekirk.wikidroid.core.testing.fake

import dev.cniekirk.wikidroid.core.seedmap.BiomeTile
import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.cniekirk.wikidroid.core.seedmap.StructurePos
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.seedmap.TILE_CELLS
import dev.cniekirk.wikidroid.core.seedmap.TileKey

/** A structure search the engine was asked for. */
data class StructureRequest(
    val world: MapWorld,
    val type: StructureType,
    val area: BlockArea,
)

/**
 * Answers from the handlers, which tests replace to script results; every request is recorded. By default
 * the world is empty: spawn at the origin, plains everywhere, no structures.
 */
class FakeSeedMapEngine : SeedMapEngine {
    var spawnHandler: suspend (MapWorld) -> BlockPos = { BlockPos(0, 0) }
    var biomeHandler: suspend (MapWorld, x: Int, z: Int) -> Int? = { _, _, _ -> PLAINS }
    var tileHandler: suspend (TileKey) -> BiomeTile = { BiomeTile(it, IntArray(TILE_CELLS * TILE_CELLS) { PLAINS }) }
    var structuresHandler: suspend (
        MapWorld,
        StructureType,
        BlockArea,
    ) -> List<StructurePos> = { _, _, _ -> emptyList() }
    var strongholdsHandler: suspend (MapWorld, count: Int) -> List<BlockPos> = { _, _ -> emptyList() }
    var colors: IntArray = IntArray(PALETTE_SIZE) { OPAQUE_BLACK }

    val spawnRequests = mutableListOf<MapWorld>()
    val biomeRequests = mutableListOf<Triple<MapWorld, Int, Int>>()
    val tileRequests = mutableListOf<TileKey>()
    val structureRequests = mutableListOf<StructureRequest>()
    val strongholdRequests = mutableListOf<Pair<MapWorld, Int>>()

    override suspend fun spawn(world: MapWorld): BlockPos {
        spawnRequests += world
        return spawnHandler(world)
    }

    override suspend fun biomeAt(
        world: MapWorld,
        x: Int,
        z: Int,
        y: Int,
    ): Int? {
        biomeRequests += Triple(world, x, z)
        return biomeHandler(world, x, z)
    }

    override suspend fun biomeTile(key: TileKey): BiomeTile {
        tileRequests += key
        return tileHandler(key)
    }

    override suspend fun structuresIn(
        world: MapWorld,
        type: StructureType,
        area: BlockArea,
    ): List<StructurePos> {
        structureRequests += StructureRequest(world, type, area)
        return structuresHandler(world, type, area)
    }

    override suspend fun strongholds(
        world: MapWorld,
        count: Int,
    ): List<BlockPos> {
        strongholdRequests += world to count
        return strongholdsHandler(world, count)
    }

    override suspend fun biomeColors(): IntArray = colors.copyOf()

    companion object {
        const val PLAINS = 1
        private const val PALETTE_SIZE = 256
        private const val OPAQUE_BLACK = 0xFF000000.toInt()
    }
}
