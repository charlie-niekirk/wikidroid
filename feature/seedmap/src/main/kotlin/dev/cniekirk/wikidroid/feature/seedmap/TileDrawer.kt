package dev.cniekirk.wikidroid.feature.seedmap

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.TILE_CELLS
import dev.cniekirk.wikidroid.core.seedmap.TileCache
import dev.cniekirk.wikidroid.core.seedmap.TileKey
import dev.cniekirk.wikidroid.core.seedmap.TileScale
import kotlin.math.roundToInt
import android.graphics.Canvas as AndroidCanvas

/**
 * Paints cached tiles onto the canvas. A tile that is not rendered yet is stood in for by the best
 * thing the cache has: the coarser tile that covers it, scaled up, or else the finer tiles inside it.
 * So panning and zooming never show holes where something could be shown, and the map sharpens in place
 * as tiles arrive. Scaling never smooths: cells stay hard-edged squares.
 *
 * One instance is kept per canvas so the scratch rectangles are not reallocated for every tile of every frame.
 */
internal class TileDrawer {
    private val paint = Paint().apply { isFilterBitmap = false }
    private val src = Rect()
    private val dst = Rect()

    fun DrawScope.drawTiles(
        camera: MapCamera,
        cache: TileCache,
        wanted: List<TileKey>,
    ) {
        drawIntoCanvas { canvas ->
            val target = canvas.nativeCanvas
            wanted.forEach { key -> draw(target, camera, cache, key) }
        }
    }

    private fun draw(
        canvas: AndroidCanvas,
        camera: MapCamera,
        cache: TileCache,
        key: TileKey,
    ) {
        dst.setToScreen(camera, key.area)
        cache.peek(key)?.let { return canvas.drawFull(it) }
        if (drawCoarser(canvas, cache, key)) return
        drawFiner(canvas, camera, cache, key)
    }

    /** Draws the part of a coarser tile that covers [key]. */
    private fun drawCoarser(
        canvas: AndroidCanvas,
        cache: TileCache,
        key: TileKey,
    ): Boolean {
        for (coarser in TileScale.entries.drop(key.scale.ordinal + 1)) {
            val ratio = coarser.blocksPerCell / key.scale.blocksPerCell
            val parent = TileKey(key.world, coarser, Math.floorDiv(key.tileX, ratio), Math.floorDiv(key.tileZ, ratio))
            val bitmap = cache.peek(parent)
            if (bitmap != null) {
                val cells = TILE_CELLS / ratio
                val left = Math.floorMod(key.tileX, ratio) * cells
                val top = Math.floorMod(key.tileZ, ratio) * cells
                src.set(left, top, left + cells, top + cells)
                canvas.drawBitmap(bitmap, src, dst, paint)
                return true
            }
        }
        return false
    }

    /** Draws whichever tiles one scale finer are ready inside [key]. */
    private fun drawFiner(
        canvas: AndroidCanvas,
        camera: MapCamera,
        cache: TileCache,
        key: TileKey,
    ) {
        val finer = TileScale.entries.getOrNull(key.scale.ordinal - 1) ?: return
        val ratio = key.scale.blocksPerCell / finer.blocksPerCell
        for (dz in 0 until ratio) {
            for (dx in 0 until ratio) {
                val child = childTile(key, finer, ratio, dx, dz)
                val bitmap = child?.let(cache::peek)
                if (child != null && bitmap != null) {
                    dst.setToScreen(camera, child.area)
                    canvas.drawFull(bitmap)
                }
            }
        }
    }

    /** The tile [dx] across and [dz] down inside [key] at the finer scale, unless that is past the border. */
    private fun childTile(
        key: TileKey,
        finer: TileScale,
        ratio: Int,
        dx: Int,
        dz: Int,
    ): TileKey? {
        val x = key.tileX * ratio + dx
        val z = key.tileZ * ratio + dz
        return if (isTileIndexInBorder(finer, x) &&
            isTileIndexInBorder(finer, z)
        ) {
            TileKey(key.world, finer, x, z)
        } else {
            null
        }
    }

    private fun AndroidCanvas.drawFull(bitmap: Bitmap) {
        src.set(0, 0, bitmap.width, bitmap.height)
        drawBitmap(bitmap, src, dst, paint)
    }

    /** Whole pixels, rounded the same way on both sides of a shared edge, so neighbouring tiles leave no seam. */
    private fun Rect.setToScreen(
        camera: MapCamera,
        area: BlockArea,
    ) = set(
        camera.screenX(area.minX.toDouble()).roundToInt(),
        camera.screenY(area.minZ.toDouble()).roundToInt(),
        camera.screenX(area.maxX.toDouble()).roundToInt(),
        camera.screenY(area.maxZ.toDouble()).roundToInt(),
    )
}
