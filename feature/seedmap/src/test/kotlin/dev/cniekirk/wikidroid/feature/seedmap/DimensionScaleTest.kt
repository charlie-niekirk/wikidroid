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

    @Test
    fun anOverworldPortalLinksToAnEighthOfItsPositionRoundedDown() {
        assertThat(BlockPos(100, -100).linkedPortal(Dimension.OVERWORLD))
            .isEqualTo(Dimension.NETHER to BlockPos(12, -13))
    }

    @Test
    fun aNetherPortalLinksToEightTimesItsPosition() {
        assertThat(BlockPos(12, -13).linkedPortal(Dimension.NETHER))
            .isEqualTo(Dimension.OVERWORLD to BlockPos(96, -104))
    }

    @Test
    fun aFarNetherPortalLinksInsideTheWorldBorder() {
        assertThat(BlockPos(30_000_000, -4_000_000).linkedPortal(Dimension.NETHER))
            .isEqualTo(Dimension.OVERWORLD to BlockPos(30_000_000, -30_000_000))
    }

    @Test
    fun endPortalsLinkNowhere() {
        assertThat(BlockPos(0, 0).linkedPortal(Dimension.END)).isNull()
    }
}
