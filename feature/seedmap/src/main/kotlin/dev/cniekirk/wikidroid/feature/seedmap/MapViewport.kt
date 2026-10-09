package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import kotlin.math.ceil

/**
 * What the map is showing: the block at the centre of a [widthPx] x [heightPx] canvas, and how many blocks
 * one pixel covers (larger means zoomed further out).
 */
@Immutable
data class MapViewport(
    val centerX: Int,
    val centerZ: Int,
    val blocksPerPixel: Float,
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(
            blocksPerPixel > 0f && blocksPerPixel.isFinite(),
        ) { "blocksPerPixel must be positive, got $blocksPerPixel" }
        require(widthPx > 0 && heightPx > 0) { "A viewport needs a size, got ${widthPx}x$heightPx" }
    }

    /** The blocks on screen, clipped to the world border. */
    val visibleArea: BlockArea
        get() {
            val halfWidth = ceil(widthPx * blocksPerPixel / 2.0).toLong()
            val halfHeight = ceil(heightPx * blocksPerPixel / 2.0).toLong()
            return clipped(centerX - halfWidth, centerZ - halfHeight, centerX + halfWidth, centerZ + halfHeight)
        }

    companion object {
        /** An area from [Long] bounds, pulled inside the world border and never empty. */
        internal fun clipped(
            minX: Long,
            minZ: Long,
            maxX: Long,
            maxZ: Long,
        ): BlockArea {
            val limit = SeedMapEngine.MAX_COORDINATE.toLong()
            return BlockArea(
                minX = minX.coerceIn(-limit, limit - 1).toInt(),
                minZ = minZ.coerceIn(-limit, limit - 1).toInt(),
                maxX = maxX.coerceIn(-limit + 1, limit).toInt(),
                maxZ = maxZ.coerceIn(-limit + 1, limit).toInt(),
            )
        }
    }
}

/** How far past the screen structures are looked up, so a little panning does not need a new search. */
internal const val PIN_PADDING = 512

/** Searches are snapped outward to this grid, so panning within a cell asks for the same area again. */
internal const val PIN_GRID = 1024

/**
 * The area to search for structures while [viewport] is showing, or null when so much of the world is on
 * screen that the search would be too wide (pins are hidden until the player zooms in).
 */
internal fun pinSearchArea(viewport: MapViewport): BlockArea? {
    val visible = viewport.visibleArea
    val minX = Math.floorDiv(visible.minX - PIN_PADDING.toLong(), PIN_GRID.toLong()) * PIN_GRID
    val minZ = Math.floorDiv(visible.minZ - PIN_PADDING.toLong(), PIN_GRID.toLong()) * PIN_GRID
    val maxX = -Math.floorDiv(-(visible.maxX + PIN_PADDING.toLong()), PIN_GRID.toLong()) * PIN_GRID
    val maxZ = -Math.floorDiv(-(visible.maxZ + PIN_PADDING.toLong()), PIN_GRID.toLong()) * PIN_GRID
    val area = MapViewport.clipped(minX, minZ, maxX, maxZ)
    val span = SeedMapEngine.MAX_STRUCTURE_SPAN
    return area.takeIf { it.width <= span && it.height <= span }
}
