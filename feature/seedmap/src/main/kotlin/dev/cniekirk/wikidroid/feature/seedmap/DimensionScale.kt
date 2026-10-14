package dev.cniekirk.wikidroid.feature.seedmap

import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import kotlin.math.roundToLong

/** One Nether block is eight overworld blocks across, which is what a portal trip relies on. */
internal const val NETHER_SCALE = 8

/**
 * Where this block is in the overworld, for the Nether, whose map is drawn in its own coordinates; null
 * in the other dimensions, which have no overworld counterpart worth showing.
 */
internal fun BlockPos.overworldEquivalent(dimension: Dimension): BlockPos? =
    if (dimension == Dimension.NETHER) BlockPos(x * NETHER_SCALE, z * NETHER_SCALE) else null

/**
 * Where a Nether portal at this block leads: the Nether at one eighth of an Overworld position (rounded down, as
 * the game does), or the Overworld at eight times a Nether one, kept inside the world border. Null in the End,
 * whose portals go nowhere you build to.
 */
internal fun BlockPos.linkedPortal(dimension: Dimension): Pair<Dimension, BlockPos>? =
    when (dimension) {
        Dimension.OVERWORLD -> {
            Dimension.NETHER to
                BlockPos(Math.floorDiv(x, NETHER_SCALE), Math.floorDiv(z, NETHER_SCALE))
        }

        Dimension.NETHER -> {
            Dimension.OVERWORLD to BlockPos(toOverworld(x), toOverworld(z))
        }

        Dimension.END -> {
            null
        }
    }

private fun toOverworld(coordinate: Int): Int {
    val limit = SeedMapEngine.MAX_COORDINATE.toLong()
    return (coordinate.toLong() * NETHER_SCALE).coerceIn(-limit, limit).toInt()
}

/**
 * [blocks] walked in this dimension as the other dimension counts them: an Overworld trip is an eighth as long
 * through the Nether, and a Nether trip covers eight times as much Overworld. Null in the End.
 */
internal fun Dimension.travelEquivalent(blocks: Long): Pair<Dimension, Long>? =
    when (this) {
        Dimension.OVERWORLD -> Dimension.NETHER to (blocks / NETHER_SCALE.toDouble()).roundToLong()
        Dimension.NETHER -> Dimension.OVERWORLD to blocks * NETHER_SCALE
        Dimension.END -> null
    }
