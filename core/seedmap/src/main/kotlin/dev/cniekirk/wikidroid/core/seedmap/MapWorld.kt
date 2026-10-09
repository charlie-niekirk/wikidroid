package dev.cniekirk.wikidroid.core.seedmap

import androidx.compose.runtime.Immutable

/** Horizontal position in blocks. */
data class BlockPos(
    val x: Int,
    val z: Int,
)

/** Everything that decides what the map looks like: the same world always produces the same tiles. */
@Immutable
data class MapWorld(
    val seed: Long,
    val version: McVersion,
    val dimension: Dimension = Dimension.OVERWORLD,
) {
    init {
        require(version.supports(dimension)) { "$dimension does not exist in Java Edition ${version.label}" }
    }
}
