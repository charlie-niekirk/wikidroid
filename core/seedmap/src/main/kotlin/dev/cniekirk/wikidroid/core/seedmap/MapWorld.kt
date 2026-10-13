package dev.cniekirk.wikidroid.core.seedmap

import androidx.compose.runtime.Immutable

/** Horizontal position in blocks. */
data class BlockPos(
    val x: Int,
    val z: Int,
) {
    /** The chunk (16 by 16 blocks) this block is in, along x. */
    val chunkX: Int get() = x shr CHUNK_SHIFT

    /** The chunk (16 by 16 blocks) this block is in, along z. */
    val chunkZ: Int get() = z shr CHUNK_SHIFT

    private companion object {
        const val CHUNK_SHIFT = 4
    }
}

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
