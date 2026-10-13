package dev.cniekirk.wikidroid.feature.seedmap

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SlimeLayerTest {
    @Test
    fun slimeChunksShowFullyAtTheDefaultZoom() {
        assertThat(slimeAlpha(MapCamera.DEFAULT_BLOCKS_PER_PIXEL)).isEqualTo(1f)
        assertThat(slimeAlpha(MapCamera.MIN_BLOCKS_PER_PIXEL)).isEqualTo(1f)
    }

    @Test
    fun slimeChunksFadeOutAsChunksShrinkToFourPixels() {
        assertThat(slimeAlpha(3f)).isWithin(0.01f).of(1f / 3f)
        assertThat(slimeAlpha(4f)).isEqualTo(0f)
        assertThat(slimeAlpha(MapCamera.MAX_BLOCKS_PER_PIXEL)).isEqualTo(0f)
    }
}
