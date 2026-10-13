package dev.cniekirk.wikidroid.feature.seedmap

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.cniekirk.wikidroid.core.seedmap.SlimeChunks
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Shades the Overworld's slime chunks green. Each 512-block region becomes a 32 by 32 bitmap, one pixel a chunk,
 * built the first time it comes on screen and kept, so panning costs nothing; it is drawn scaled up without
 * smoothing, so chunks stay hard-edged squares that line up with the chunk grid.
 *
 * The layer fades in as chunks grow from [FADE_START_PX] to [FADE_END_PX] across, the same way the grid's
 * chunk lines do, so a wide view is never a green haze. One instance is kept per canvas.
 */
internal class SlimeLayer {
    private val regions = LruCache<RegionKey, Bitmap>(MAX_REGIONS)
    private val paint = Paint().apply { isFilterBitmap = false }
    private val src = Rect(0, 0, SlimeChunks.REGION_CHUNKS, SlimeChunks.REGION_CHUNKS)
    private val dst = Rect()

    fun DrawScope.drawSlimeChunks(
        camera: MapCamera,
        seed: Long,
    ) {
        val alpha = slimeAlpha(camera.blocksPerPixel)
        if (alpha <= 0f) return
        paint.alpha = (alpha * FILL_ALPHA * OPAQUE).roundToInt()
        val minX = regionOf(camera.worldX(0f))
        val maxX = regionOf(camera.worldX(size.width))
        val minZ = regionOf(camera.worldZ(0f))
        val maxZ = regionOf(camera.worldZ(size.height))
        drawIntoCanvas { canvas ->
            val target = canvas.nativeCanvas
            for (regionZ in minZ..maxZ) {
                for (regionX in minX..maxX) {
                    // Both edges from world positions, rounded alike, so neighbouring regions neither gap nor overlap.
                    dst.set(
                        camera.screenX(regionX.toDouble() * REGION_BLOCKS).roundToInt(),
                        camera.screenY(regionZ.toDouble() * REGION_BLOCKS).roundToInt(),
                        camera.screenX((regionX + 1).toDouble() * REGION_BLOCKS).roundToInt(),
                        camera.screenY((regionZ + 1).toDouble() * REGION_BLOCKS).roundToInt(),
                    )
                    target.drawBitmap(region(seed, regionX, regionZ), src, dst, paint)
                }
            }
        }
    }

    private fun region(
        seed: Long,
        regionX: Int,
        regionZ: Int,
    ): Bitmap {
        val key = RegionKey(seed, regionX, regionZ)
        return regions.get(key) ?: slimeBitmap(SlimeChunks.region(seed, regionX, regionZ)).also { regions.put(key, it) }
    }

    private data class RegionKey(
        val seed: Long,
        val regionX: Int,
        val regionZ: Int,
    )

    private companion object {
        /** More than a tall tablet shows at the widest zoom the layer is drawn at. */
        const val MAX_REGIONS = 512
        const val FILL_ALPHA = 0.5f
        const val OPAQUE = 255

        /** Regions past the world border hold no chunks, so the range stops at the last region inside it. */
        const val BORDER_REGION = SeedMapEngine.MAX_COORDINATE / REGION_BLOCKS

        fun regionOf(block: Double): Int =
            Math
                .floorDiv(floor(block).toLong(), REGION_BLOCKS.toLong())
                .coerceIn(-BORDER_REGION - 1L, BORDER_REGION.toLong())
                .toInt()
    }
}

/** How strongly slime chunks show at [blocksPerPixel]: 0 while a chunk is under [FADE_START_PX] across. */
internal fun slimeAlpha(blocksPerPixel: Float): Float =
    ((CHUNK_BLOCKS / blocksPerPixel - FADE_START_PX) / (FADE_END_PX - FADE_START_PX)).coerceIn(0f, 1f)

private fun slimeBitmap(chunks: BooleanArray): Bitmap {
    val pixels = IntArray(chunks.size) { if (chunks[it]) SLIME_GREEN else TRANSPARENT }
    return Bitmap.createBitmap(pixels, SlimeChunks.REGION_CHUNKS, SlimeChunks.REGION_CHUNKS, Bitmap.Config.ARGB_8888)
}

private const val FADE_START_PX = 4f
private const val FADE_END_PX = 8f
private const val SLIME_GREEN = 0xFF4CD964.toInt()

/** The slime layer's green, for the selection sheet's slime chunk line. */
internal val SlimeChunkGreen = Color(SLIME_GREEN)
private const val TRANSPARENT = 0
