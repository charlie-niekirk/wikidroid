package dev.cniekirk.wikidroid.feature.seedmap

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GridLinesTest {
    @Test
    fun linesFallOnMultiplesOfTheSpacing() {
        assertThat(gridLines(-40.0, 40.0, 16).toList()).containsExactly(-32, -16, 0, 16, 32).inOrder()
        assertThat(gridLines(0.0, 16.0, 16).toList()).containsExactly(0, 16).inOrder()
        assertThat(gridLines(1.0, 15.0, 16).toList()).isEmpty()
    }

    @Test
    fun regionLinesCoverNegativeAndPositiveRegions() {
        assertThat(gridLines(-1100.0, 600.0, REGION_BLOCKS).toList()).containsExactly(-1024, -512, 0, 512).inOrder()
    }

    @Test
    fun zoomedInShowsChunksAndRegionsAtFullStrength() {
        val levels = gridLevels(blocksPerPixel = 0.5f)

        assertThat(levels.map { it.spacing }).containsExactly(CHUNK_BLOCKS, REGION_BLOCKS)
        assertThat(levels.map { it.alpha }).containsExactly(1f, 1f)
    }

    @Test
    fun chunkLinesFadeOutBeforeTheyCrowdTogether() {
        // Chunks 4 px apart at 4 blocks per pixel: too close to draw.
        assertThat(gridLevels(blocksPerPixel = 4f).map { it.spacing }).containsExactly(REGION_BLOCKS)
        // 11 px apart: half way through the fade.
        val chunk = gridLevels(blocksPerPixel = 16f / 11f).first { it.spacing == CHUNK_BLOCKS }
        assertThat(chunk.alpha).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun zoomedRightOutShowsNoGrid() {
        assertThat(gridLevels(MapCamera.MAX_BLOCKS_PER_PIXEL)).isEmpty()
    }
}
