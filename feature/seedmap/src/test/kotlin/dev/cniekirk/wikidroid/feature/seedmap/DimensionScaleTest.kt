package dev.cniekirk.wikidroid.feature.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import org.junit.Test

class DimensionScaleTest {
    @Test
    fun aNetherBlockIsEightOverworldBlocks() {
        assertThat(BlockPos(12, -5).overworldEquivalent(Dimension.NETHER)).isEqualTo(BlockPos(96, -40))
    }

    @Test
    fun otherDimensionsHaveNoOverworldEquivalent() {
        assertThat(BlockPos(12, -5).overworldEquivalent(Dimension.OVERWORLD)).isNull()
        assertThat(BlockPos(12, -5).overworldEquivalent(Dimension.END)).isNull()
    }

    @Test
    fun theFarthestNetherBlockStillFitsAnInt() {
        val far = BlockPos(30_000_000, -30_000_000).overworldEquivalent(Dimension.NETHER)

        assertThat(far).isEqualTo(BlockPos(240_000_000, -240_000_000))
    }
}
