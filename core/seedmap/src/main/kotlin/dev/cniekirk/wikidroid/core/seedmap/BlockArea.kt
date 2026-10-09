package dev.cniekirk.wikidroid.core.seedmap

import androidx.compose.runtime.Immutable

/** A rectangle of blocks: `minX <= x < maxX` and `minZ <= z < maxZ`. */
@Immutable
data class BlockArea(
    val minX: Int,
    val minZ: Int,
    val maxX: Int,
    val maxZ: Int,
) {
    init {
        require(maxX > minX && maxZ > minZ) { "An area needs a positive width and height: $this" }
    }

    val width: Int get() = maxX - minX
    val height: Int get() = maxZ - minZ

    operator fun contains(pos: BlockPos): Boolean = pos.x in minX until maxX && pos.z in minZ until maxZ

    /** The area grown by [blocks] on every side, for pins whose structure reaches over the edge. */
    fun expandedBy(blocks: Int): BlockArea = BlockArea(minX - blocks, minZ - blocks, maxX + blocks, maxZ + blocks)
}
