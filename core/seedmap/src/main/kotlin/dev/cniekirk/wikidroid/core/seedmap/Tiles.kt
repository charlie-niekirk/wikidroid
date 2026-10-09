package dev.cniekirk.wikidroid.core.seedmap

import androidx.compose.runtime.Immutable

/**
 * How many blocks one biome cell covers. A tile is always [TILE_CELLS] cells square, so a coarser scale
 * covers more of the world with the same work.
 */
enum class TileScale(
    val blocksPerCell: Int,
) {
    CELL_4(4),
    CELL_16(16),
    CELL_64(64),
    CELL_256(256),
    ;

    /** Blocks along one side of a tile at this scale. */
    val tileSpan: Int get() = TILE_CELLS * blocksPerCell

    companion object {
        /**
         * The coarsest scale whose cells are no bigger than a screen pixel at [blocksPerPixel], so the map
         * stays sharp; zoomed in past the finest scale this is [CELL_4], which is drawn enlarged.
         */
        fun forBlocksPerPixel(blocksPerPixel: Float): TileScale =
            entries.lastOrNull { it.blocksPerCell <= blocksPerPixel } ?: CELL_4
    }
}

const val TILE_CELLS = 256

/** One tile of the map: tile ([tileX], [tileZ]) at [scale], counted from the origin in whole tiles. */
@Immutable
data class TileKey(
    val world: MapWorld,
    val scale: TileScale,
    val tileX: Int,
    val tileZ: Int,
) {
    init {
        // Tiles past the world border are refused up front, which also keeps `area` clear of Int overflow.
        val limit = SeedMapEngine.MAX_COORDINATE / scale.tileSpan + 1
        require(
            tileX in -limit..limit && tileZ in -limit..limit,
        ) { "Tile ($tileX, $tileZ) is outside the world border" }
    }

    /** The blocks this tile covers. */
    val area: BlockArea
        get() {
            val span = scale.tileSpan
            return BlockArea(tileX * span, tileZ * span, (tileX + 1) * span, (tileZ + 1) * span)
        }

    companion object {
        /** Every tile of [world] at [scale] that overlaps [area], row by row. */
        fun covering(
            world: MapWorld,
            scale: TileScale,
            area: BlockArea,
        ): List<TileKey> {
            val span = scale.tileSpan
            val firstX = Math.floorDiv(area.minX, span)
            val lastX = Math.floorDiv(area.maxX - 1, span)
            val firstZ = Math.floorDiv(area.minZ, span)
            val lastZ = Math.floorDiv(area.maxZ - 1, span)
            return buildList {
                for (z in firstZ..lastZ) {
                    for (x in firstX..lastX) add(TileKey(world, scale, x, z))
                }
            }
        }
    }
}

/**
 * The biome ids of one tile: `biomes[z * TILE_CELLS + x]` is the cell [x] cells east and [z] cells south of
 * the tile's north-west corner. Ids are cubiomes' `BiomeID` values.
 */
class BiomeTile(
    val key: TileKey,
    val biomes: IntArray,
) {
    init {
        require(
            biomes.size == TILE_CELLS * TILE_CELLS,
        ) { "A tile has ${TILE_CELLS * TILE_CELLS} cells, got ${biomes.size}" }
    }
}

/** A structure found by the engine: its type and the block where its generation attempt sits. */
@Immutable
data class StructurePos(
    val type: StructureType,
    val pos: BlockPos,
)
