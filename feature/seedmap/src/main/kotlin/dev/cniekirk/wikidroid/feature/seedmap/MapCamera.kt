package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Where the map is looking: the block at the middle of the canvas and how many blocks a pixel covers.
 * The canvas pans and zooms it, and reads it back while drawing, so every property is observable and
 * changing one redraws the map without recomposing anything.
 *
 * The centre is kept as a [Double] so a slow drag at a deep zoom does not round to nothing. Screen
 * coordinates are pixels from the canvas's top-left corner; world coordinates are blocks, x east and z south.
 */
@Stable
class MapCamera(
    centerX: Double = 0.0,
    centerZ: Double = 0.0,
    blocksPerPixel: Float = DEFAULT_BLOCKS_PER_PIXEL,
) {
    var centerX: Double by mutableDoubleStateOf(centerX.coerceIn(-LIMIT, LIMIT))
        private set
    var centerZ: Double by mutableDoubleStateOf(centerZ.coerceIn(-LIMIT, LIMIT))
        private set
    var blocksPerPixel: Float by mutableFloatStateOf(
        blocksPerPixel.coerceIn(MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL),
    )
        private set
    var widthPx: Int by mutableIntStateOf(0)
        private set
    var heightPx: Int by mutableIntStateOf(0)
        private set

    /** False until the canvas has been measured; nothing can be projected before that. */
    val hasSize: Boolean get() = widthPx > 0 && heightPx > 0

    fun resize(
        widthPx: Int,
        heightPx: Int,
    ) {
        this.widthPx = widthPx
        this.heightPx = heightPx
    }

    /** Moves the map by a finger drag of ([dxPx], [dyPx]): the content follows the finger. */
    fun panBy(
        dxPx: Float,
        dyPx: Float,
    ) {
        centerX = (centerX - dxPx * blocksPerPixel).coerceIn(-LIMIT, LIMIT)
        centerZ = (centerZ - dyPx * blocksPerPixel).coerceIn(-LIMIT, LIMIT)
    }

    /**
     * Zooms in by [factor] (below 1 zooms out), keeping the block under ([focusXPx], [focusYPx]) where it
     * is on screen, so a pinch or a double tap grows the map around the fingers. Stops at the zoom limits.
     */
    fun zoomBy(
        factor: Float,
        focusXPx: Float = widthPx / 2f,
        focusZPx: Float = heightPx / 2f,
    ) {
        if (factor <= 0f || !factor.isFinite()) return
        val focusX = worldX(focusXPx)
        val focusZ = worldZ(focusZPx)
        blocksPerPixel = (blocksPerPixel / factor).coerceIn(MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL)
        centerX = (focusX - (focusXPx - widthPx / 2.0) * blocksPerPixel).coerceIn(-LIMIT, LIMIT)
        centerZ = (focusZ - (focusZPx - heightPx / 2.0) * blocksPerPixel).coerceIn(-LIMIT, LIMIT)
    }

    /** Puts [pos] in the middle of the canvas, keeping the zoom. */
    fun centerOn(pos: BlockPos) {
        centerX = pos.x.toDouble().coerceIn(-LIMIT, LIMIT)
        centerZ = pos.z.toDouble().coerceIn(-LIMIT, LIMIT)
    }

    fun worldX(screenX: Float): Double = centerX + (screenX - widthPx / 2.0) * blocksPerPixel

    fun worldZ(screenY: Float): Double = centerZ + (screenY - heightPx / 2.0) * blocksPerPixel

    fun screenX(worldX: Double): Float = ((worldX - centerX) / blocksPerPixel + widthPx / 2.0).toFloat()

    fun screenY(worldZ: Double): Float = ((worldZ - centerZ) / blocksPerPixel + heightPx / 2.0).toFloat()

    /** The block under a screen point, pulled inside the world border. */
    fun blockAt(
        screenX: Float,
        screenY: Float,
    ): BlockPos {
        val limit = SeedMapEngine.MAX_COORDINATE
        return BlockPos(
            Math.floor(worldX(screenX)).toInt().coerceIn(-limit, limit),
            Math.floor(worldZ(screenY)).toInt().coerceIn(-limit, limit),
        )
    }

    /** What the view model needs to know about the view, or null until the canvas has a size. */
    fun viewport(): MapViewport? =
        if (hasSize) {
            MapViewport(centerX.roundToInt(), centerZ.roundToInt(), blocksPerPixel, widthPx, heightPx)
        } else {
            null
        }

    /** The blocks on screen, clipped to the world border, or null until the canvas has a size. */
    fun visibleArea(): BlockArea? {
        if (!hasSize) return null
        val halfWidth = ceil(widthPx * blocksPerPixel / 2.0).toLong()
        val halfHeight = ceil(heightPx * blocksPerPixel / 2.0).toLong()
        return MapViewport.clipped(
            minX = Math.floor(centerX).toLong() - halfWidth,
            minZ = Math.floor(centerZ).toLong() - halfHeight,
            maxX = Math.ceil(centerX).toLong() + halfWidth,
            maxZ = Math.ceil(centerZ).toLong() + halfHeight,
        )
    }

    companion object {
        /** Deepest zoom: four pixels to a block. */
        const val MIN_BLOCKS_PER_PIXEL = 0.25f

        /** Widest zoom: the coarsest tile scale is then one cell to a pixel. */
        const val MAX_BLOCKS_PER_PIXEL = 256f

        const val DEFAULT_BLOCKS_PER_PIXEL = 3f

        private val LIMIT = SeedMapEngine.MAX_COORDINATE.toDouble()

        val Saver: Saver<MapCamera, Any> =
            listSaver(
                save = { listOf(it.centerX, it.centerZ, it.blocksPerPixel) },
                restore = { MapCamera(it[0] as Double, it[1] as Double, it[2] as Float) },
            )
    }
}

/** A camera that survives rotation, so the player keeps their place. */
@Composable
fun rememberMapCamera(): MapCamera = rememberSaveable(saver = MapCamera.Saver) { MapCamera() }
