package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Calls [onLongPress] when one finger rests on the map, without moving, for the long-press timeout.
 *
 * `detectTapGestures(onLongPress = ...)` is not used because it ignores a second finger: a two-finger hold or a
 * slow pinch would count as a long press, and its consume-until-up would then stop the pinch. This gives up as
 * soon as a second finger lands, the finger moves past touch slop, or another detector (pan, zoom) consumes a
 * move. When it fires it consumes the rest of the gesture, so the lift is not also a tap.
 */
internal suspend fun PointerInputScope.detectLongPressOnly(onLongPress: (Offset) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Null when the timeout ran out with the finger still resting there.
        val gaveUp =
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var cancelled = false
                while (!cancelled) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    cancelled = event.changes.size > 1 ||
                        change == null ||
                        !change.pressed || // Lifted; the tap detector has usually consumed the up already.
                        // The tap detector consumes the down itself; only a consumed move means a pan took over.
                        (change.isConsumed && change.positionChangeIgnoreConsumed() != Offset.Zero) ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                }
                true
            }
        if (gaveUp == null) {
            onLongPress(down.position)
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
