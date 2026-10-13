package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

/** One set of evenly spaced grid lines: every [spacing] blocks, drawn at [alpha] (0 is not drawn). */
internal data class GridLevel(
    val spacing: Int,
    val alpha: Float,
    val major: Boolean,
)

internal const val CHUNK_BLOCKS = 16
internal const val REGION_BLOCKS = 512

/**
 * Chunk lines (every 16 blocks) and region lines (every 512, the size of a region file), each faded in as the
 * zoom spreads them out so the grid never turns into a solid wash. Chunk lines start to show at
 * [CHUNK_FADE_START_PX] apart and region lines at [REGION_FADE_START_PX].
 */
internal fun gridLevels(blocksPerPixel: Float): List<GridLevel> =
    listOf(
        GridLevel(CHUNK_BLOCKS, fade(CHUNK_BLOCKS / blocksPerPixel, CHUNK_FADE_START_PX, CHUNK_FADE_END_PX), false),
        GridLevel(REGION_BLOCKS, fade(REGION_BLOCKS / blocksPerPixel, REGION_FADE_START_PX, REGION_FADE_END_PX), true),
    ).filter { it.alpha > 0f }

private fun fade(
    gapPx: Float,
    start: Float,
    end: Float,
): Float = ((gapPx - start) / (end - start)).coerceIn(0f, 1f)

/** The multiples of [spacing] from [min] to [max], which are where that level's lines fall. */
internal fun gridLines(
    min: Double,
    max: Double,
    spacing: Int,
): IntProgression {
    val first = ceil(min / spacing).toLong() * spacing
    val last = floor(max / spacing).toLong() * spacing
    return first.toInt()..last.toInt() step spacing
}

/** Draws [gridLevels] over the map; region lines are thicker, and every line has a dark edge to show on snow. */
internal fun DrawScope.drawGrid(camera: MapCamera) {
    val minX = camera.worldX(0f)
    val maxX = camera.worldX(size.width)
    val minZ = camera.worldZ(0f)
    val maxZ = camera.worldZ(size.height)
    gridLevels(camera.blocksPerPixel).forEach { level ->
        val width = (if (level.major) 2.dp else 1.dp).toPx()
        val color = Color.White.copy(alpha = level.alpha * (if (level.major) MAJOR_ALPHA else MINOR_ALPHA))
        val shadow = Color.Black.copy(alpha = level.alpha * SHADOW_ALPHA)
        for (x in gridLines(minX, maxX, level.spacing)) {
            val sx = camera.screenX(x.toDouble())
            drawLine(shadow, Offset(sx + width, 0f), Offset(sx + width, size.height), width)
            drawLine(color, Offset(sx, 0f), Offset(sx, size.height), width)
        }
        for (z in gridLines(minZ, maxZ, level.spacing)) {
            val sy = camera.screenY(z.toDouble())
            drawLine(shadow, Offset(0f, sy + width), Offset(size.width, sy + width), width)
            drawLine(color, Offset(0f, sy), Offset(size.width, sy), width)
        }
    }
}

private const val CHUNK_FADE_START_PX = 6f
private const val CHUNK_FADE_END_PX = 16f
private const val REGION_FADE_START_PX = 12f
private const val REGION_FADE_END_PX = 32f
private const val MAJOR_ALPHA = 0.75f
private const val MINOR_ALPHA = 0.4f
private const val SHADOW_ALPHA = 0.25f
