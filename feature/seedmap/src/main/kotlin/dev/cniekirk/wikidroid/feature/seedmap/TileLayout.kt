package dev.cniekirk.wikidroid.feature.seedmap

import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.cniekirk.wikidroid.core.seedmap.TileKey
import dev.cniekirk.wikidroid.core.seedmap.TileScale

/**
 * A biome cell may be up to this many screen pixels across before the map switches to a finer tile scale.
 * Insisting on one cell per pixel would ask for sixteen times the tiles at every step for a difference
 * nobody can see on a phone.
 */
internal const val MAX_CELL_PIXELS = 2f

/** The most tiles asked for at once, so a very wide window cannot ask for more than the cache holds. */
internal const val MAX_WANTED_TILES = 96

/** The tile scale to draw at [blocksPerPixel]: the coarsest whose cells are at most [MAX_CELL_PIXELS] wide. */
internal fun tileScaleFor(blocksPerPixel: Float): TileScale =
    TileScale.forBlocksPerPixel(blocksPerPixel * MAX_CELL_PIXELS)

/**
 * The tiles that cover what [camera] shows, nearest the middle of the screen first so those fill in first.
 * Empty until the canvas has a size.
 */
internal fun wantedTiles(
    camera: MapCamera,
    world: MapWorld,
): List<TileKey> {
    val area = camera.visibleArea() ?: return emptyList()
    val tiles = TileKey.covering(world, tileScaleFor(camera.blocksPerPixel), area)
    val centerX = camera.centerX
    val centerZ = camera.centerZ
    return tiles
        .sortedBy { key ->
            val tile = key.area
            val dx = (tile.minX + tile.maxX) / 2.0 - centerX
            val dz = (tile.minZ + tile.maxZ) / 2.0 - centerZ
            dx * dx + dz * dz
        }.take(MAX_WANTED_TILES)
}

/** Whether tile [index] at [scale] is one [TileKey] accepts (inside the world border, give or take a tile). */
internal fun isTileIndexInBorder(
    scale: TileScale,
    index: Int,
): Boolean =
    index in -(SeedMapEngine.MAX_COORDINATE / scale.tileSpan + 1)..(SeedMapEngine.MAX_COORDINATE / scale.tileSpan + 1)
