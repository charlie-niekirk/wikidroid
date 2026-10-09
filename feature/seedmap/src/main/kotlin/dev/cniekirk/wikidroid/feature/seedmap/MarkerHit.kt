package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.ui.geometry.Offset
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.StructurePos
import dev.cniekirk.wikidroid.core.seedmap.StructureType

/** What a tap landed on, other than bare map. */
internal sealed interface MarkerHit {
    data class Structure(
        val structure: StructurePos,
    ) : MarkerHit

    data object Spawn : MarkerHit
}

/**
 * The marker nearest [tap] within [radiusPx], or null if the tap was on bare map. Spawn, strongholds
 * and structure pins compete on distance alone; on a tie the one listed first (spawn, then strongholds,
 * then pins) wins.
 */
internal fun hitTest(
    camera: MapCamera,
    tap: Offset,
    spawn: BlockPos?,
    strongholds: List<BlockPos>,
    pins: List<StructurePos>,
    radiusPx: Float,
): MarkerHit? {
    var best: MarkerHit? = null
    var bestDistance = radiusPx * radiusPx

    fun consider(
        pos: BlockPos,
        hit: () -> MarkerHit,
    ) {
        val dx = camera.screenX(pos.x.toDouble()) - tap.x
        val dy = camera.screenY(pos.z.toDouble()) - tap.y
        val distance = dx * dx + dy * dy
        if (distance < bestDistance) {
            bestDistance = distance
            best = hit()
        }
    }

    spawn?.let { consider(it) { MarkerHit.Spawn } }
    strongholds.forEach { pos -> consider(pos) { MarkerHit.Structure(StructurePos(StructureType.STRONGHOLD, pos)) } }
    pins.forEach { pin -> consider(pin.pos) { MarkerHit.Structure(pin) } }
    return best
}
