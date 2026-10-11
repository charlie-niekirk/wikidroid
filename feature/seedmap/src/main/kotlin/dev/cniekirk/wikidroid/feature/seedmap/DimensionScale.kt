package dev.cniekirk.wikidroid.feature.seedmap

import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension

/** One Nether block is eight overworld blocks across, which is what a portal trip relies on. */
internal const val NETHER_SCALE = 8

/**
 * Where this block is in the overworld, for the Nether, whose map is drawn in its own coordinates; null
 * in the other dimensions, which have no overworld counterpart worth showing.
 */
internal fun BlockPos.overworldEquivalent(dimension: Dimension): BlockPos? =
    if (dimension == Dimension.NETHER) BlockPos(x * NETHER_SCALE, z * NETHER_SCALE) else null
