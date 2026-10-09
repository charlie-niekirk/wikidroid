package dev.cniekirk.wikidroid.core.seedmap

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TileCacheBytesTest {
    @Test
    fun `the cache gets an eighth of the heap limit`() {
        assertThat(tileCacheBytes(memoryClassMb = 256)).isEqualTo(32 * 1024 * 1024)
        assertThat(tileCacheBytes(memoryClassMb = 512)).isEqualTo(64 * 1024 * 1024)
    }

    @Test
    fun `a tiny heap still fits one tile`() {
        assertThat(tileCacheBytes(memoryClassMb = 1)).isEqualTo(TILE_CELLS * TILE_CELLS * 4)
    }

    @Test
    fun `a huge heap limit does not overflow`() {
        assertThat(tileCacheBytes(memoryClassMb = Int.MAX_VALUE)).isEqualTo(Int.MAX_VALUE)
    }
}
