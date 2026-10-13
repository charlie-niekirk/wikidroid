package dev.cniekirk.wikidroid.core.domain.seedmap

import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.cniekirk.wikidroid.core.seedmap.StructurePos
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs

/**
 * The structure of a type closest to a point, or null if there is none within [MAX_RINGS] rings.
 *
 * Region-based types are searched in square rings of [CELL_BLOCKS]-wide cells around the point, one ring at a
 * time with its cells in parallel. Once the rings searched reach past the closest structure found, nothing
 * unsearched can be closer, so the search stops there; usually that is the first ring. Strongholds come from the
 * engine's list instead. A cell the engine fails on counts as empty.
 */
@Inject
class FindNearestStructureUseCase(
    private val engine: SeedMapEngine,
) {
    suspend operator fun invoke(
        world: MapWorld,
        type: StructureType,
        from: BlockPos,
    ): StructurePos? =
        when {
            !type.isAvailableIn(world) -> {
                null
            }

            type == StructureType.STRONGHOLD -> {
                attempt { engine.strongholds(world) }
                    .orEmpty()
                    .minByOrNull { it.distanceSquaredTo(from) }
                    ?.let { StructurePos(type, it) }
            }

            else -> {
                nearestByRegion(world, type, from)
            }
        }

    private suspend fun nearestByRegion(
        world: MapWorld,
        type: StructureType,
        from: BlockPos,
    ): StructurePos? {
        var best: StructurePos? = null
        for (ring in 0 until MAX_RINGS) {
            val found = search(world, type, ringCells(from, ring))
            best = (listOfNotNull(best) + found).minByOrNull { it.pos.distanceSquaredTo(from) }
            // Everything within this many blocks of `from` on both axes has now been searched.
            val reach = CELL_BLOCKS / 2L + ring * CELL_BLOCKS.toLong()
            if (best != null && best.pos.distanceSquaredTo(from) <= reach * reach) return best
        }
        return best
    }

    private suspend fun search(
        world: MapWorld,
        type: StructureType,
        cells: List<BlockArea>,
    ): List<StructurePos> =
        coroutineScope {
            cells
                .map { cell -> async { attempt { engine.structuresIn(world, type, cell) }.orEmpty() } }
                .awaitAll()
                .flatten()
        }

    companion object {
        /** Half the widest area the engine searches at once, so a cell is a quick query. */
        const val CELL_BLOCKS = SeedMapEngine.MAX_STRUCTURE_SPAN / 2

        /** Ring 0 is the cell around the point; with rings 1 to 4 the search reaches 18,432 blocks out (81 cells). */
        const val MAX_RINGS = 5

        /** How far the search reaches from the point along each axis: what "within reach" means. */
        const val REACH_BLOCKS = CELL_BLOCKS / 2 + (MAX_RINGS - 1) * CELL_BLOCKS
    }
}

/**
 * The cells of ring [ring] around [center]: the one cell centred on it for ring 0, otherwise the 8 x [ring] cells
 * that frame the rings inside. Cells are clipped to the world border, and those wholly outside it are dropped.
 */
internal fun ringCells(
    center: BlockPos,
    ring: Int,
): List<BlockArea> {
    val size = FindNearestStructureUseCase.CELL_BLOCKS.toLong()
    val originX = center.x - size / 2
    val originZ = center.z - size / 2
    return buildList {
        for (i in -ring..ring) {
            for (j in -ring..ring) {
                if (maxOf(abs(i), abs(j)) != ring) continue
                clippedCell(originX + i * size, originZ + j * size, size)?.let(::add)
            }
        }
    }
}

private fun clippedCell(
    minX: Long,
    minZ: Long,
    size: Long,
): BlockArea? {
    val limit = SeedMapEngine.MAX_COORDINATE.toLong()
    val x0 = minX.coerceAtLeast(-limit)
    val z0 = minZ.coerceAtLeast(-limit)
    val x1 = (minX + size).coerceAtMost(limit)
    val z1 = (minZ + size).coerceAtMost(limit)
    return if (x1 > x0 && z1 > z0) BlockArea(x0.toInt(), z0.toInt(), x1.toInt(), z1.toInt()) else null
}

private fun BlockPos.distanceSquaredTo(other: BlockPos): Long {
    val dx = (x - other.x).toLong()
    val dz = (z - other.z).toLong()
    return dx * dx + dz * dz
}
