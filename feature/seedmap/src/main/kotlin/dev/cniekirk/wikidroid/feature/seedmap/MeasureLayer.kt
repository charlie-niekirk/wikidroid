package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.seedmap.BlockPos

/** The ruler: a dashed white line between the measurement's ends, each end a dot, with a dark edge for snow. */
internal fun DrawScope.drawMeasurement(
    camera: MapCamera,
    measure: Measurement,
) {
    fun at(pos: BlockPos) = Offset(camera.screenX(pos.x.toDouble()), camera.screenY(pos.z.toDouble()))

    val start = measure.start?.let(::at) ?: return
    val end = measure.end?.let(::at)
    if (end != null) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), GAP.toPx()))
        drawLine(Color.Black.copy(alpha = SHADOW_ALPHA), start, end, LINE.toPx() + EDGE.toPx(), StrokeCap.Round, dash)
        drawLine(Color.White, start, end, LINE.toPx(), StrokeCap.Round, dash)
    }
    listOfNotNull(start, end).forEach { point ->
        drawCircle(Color.Black.copy(alpha = SHADOW_ALPHA), DOT.toPx() + EDGE.toPx(), point)
        drawCircle(Color.White, DOT.toPx(), point)
    }
}

private val LINE = 3.dp
private val EDGE = 2.dp
private val DOT = 6.dp
private val DASH = 10.dp
private val GAP = 6.dp
private const val SHADOW_ALPHA = 0.6f
