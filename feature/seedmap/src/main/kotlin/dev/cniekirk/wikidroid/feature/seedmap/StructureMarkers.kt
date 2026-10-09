@file:Suppress("MagicNumber") // The outlines are proportions of the marker radius, not tunable settings.

package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The outline of a map marker. Types differ by shape as well as colour, so colour alone is never the key. */
internal enum class MarkerShape { Circle, Square, Diamond, Triangle, Star, Ring }

internal data class MarkerStyle(
    val color: Color,
    val shape: MarkerShape,
)

private val MarkerColors =
    listOf(
        Color(0xFFE53935),
        Color(0xFF1E88E5),
        Color(0xFF43A047),
        Color(0xFFFB8C00),
        Color(0xFF8E24AA),
        Color(0xFF00ACC1),
        Color(0xFFFDD835),
        Color(0xFFF06292),
    )

private val MarkerShapes = listOf(MarkerShape.Circle, MarkerShape.Square, MarkerShape.Diamond, MarkerShape.Triangle)

/** Every structure gets its own colour-and-shape pair, derived from its place in the enum. */
internal fun StructureType.markerStyle(): MarkerStyle =
    if (this == StructureType.STRONGHOLD) {
        MarkerStyle(Color(0xFF2E7D32), MarkerShape.Ring)
    } else {
        MarkerStyle(
            MarkerColors[ordinal % MarkerColors.size],
            MarkerShapes[
                ordinal / MarkerColors.size %
                    MarkerShapes.size,
            ],
        )
    }

internal val SpawnMarkerStyle = MarkerStyle(Color(0xFFFFD600), MarkerShape.Star)

/** Radius of a marker on the map. */
internal val MarkerRadius: Dp = 9.dp

private val OutlineDark = Color(0xB3000000)
private val OutlineLight = Color.White

/** Draws one marker centred on [center]; [path] is scratch space the caller reuses between markers. */
internal fun DrawScope.drawMarker(
    style: MarkerStyle,
    center: Offset,
    radius: Float,
    path: Path,
) {
    val thin = radius * 0.22f
    path.rewind()
    when (style.shape) {
        MarkerShape.Ring -> {
            drawCircle(OutlineDark, radius, center, style = Stroke(thin * 3.2f))
            drawCircle(OutlineLight, radius, center, style = Stroke(thin * 2f))
            drawCircle(style.color, radius, center, style = Stroke(thin * 1.1f))
            drawCircle(OutlineDark, radius * 0.38f, center)
            drawCircle(style.color, radius * 0.28f, center)
        }

        MarkerShape.Circle -> {
            drawCircle(OutlineDark, radius + thin * 1.6f, center)
            drawCircle(OutlineLight, radius + thin * 0.6f, center)
            drawCircle(style.color, radius - thin * 0.4f, center)
        }

        else -> {
            for ((grow, color) in listOf(
                thin * 1.6f to OutlineDark,
                thin * 0.6f to OutlineLight,
                -thin * 0.4f to style.color,
            )) {
                path.rewind()
                path.addShape(style.shape, center, radius + grow)
                drawPath(path, color)
            }
        }
    }
}

private const val STAR_POINTS = 5
private const val STAR_INNER_RATIO = 0.45f

private fun Path.addShape(
    shape: MarkerShape,
    center: Offset,
    radius: Float,
) {
    when (shape) {
        MarkerShape.Square -> {
            val half = radius * 0.85f
            moveTo(center.x - half, center.y - half)
            lineTo(center.x + half, center.y - half)
            lineTo(center.x + half, center.y + half)
            lineTo(center.x - half, center.y + half)
        }

        MarkerShape.Diamond -> {
            val reach = radius * 1.15f
            moveTo(center.x, center.y - reach)
            lineTo(center.x + reach, center.y)
            lineTo(center.x, center.y + reach)
            lineTo(center.x - reach, center.y)
        }

        MarkerShape.Triangle -> {
            moveTo(center.x, center.y - radius * 1.1f)
            lineTo(center.x + radius * 1.05f, center.y + radius * 0.8f)
            lineTo(center.x - radius * 1.05f, center.y + radius * 0.8f)
        }

        MarkerShape.Star -> {
            repeat(STAR_POINTS * 2) { i ->
                val r = if (i % 2 == 0) radius * 1.25f else radius * 1.25f * STAR_INNER_RATIO
                val angle = -PI / 2 + i * PI / STAR_POINTS
                val x = center.x + (r * cos(angle)).toFloat()
                val y = center.y + (r * sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        MarkerShape.Circle, MarkerShape.Ring -> {
            addOval(Rect(center, radius))
        }
    }
    close()
}

/** A marker as a small icon, for the filter chips, so the chip is the legend for the map. */
@Composable
internal fun MarkerIcon(
    style: MarkerStyle,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    val path = remember { Path() }
    Canvas(modifier.size(size)) {
        drawMarker(style, center, this.size.minDimension / 2f * 0.62f, path)
    }
}
