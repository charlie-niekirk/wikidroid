package dev.cniekirk.wikidroid.core.seedmap

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The expected chunks come from cubiomes' own `isSlimeChunk`, built on the host:
 * `clang -fwrapv -I core/seedmap/src/main/cpp/cubiomes window.c`, where `window.c` prints every slime chunk in a
 * 16 by 16 window from each origin below. The windows cover the overflow paths: the world border, `x * x` past
 * `Int.MAX_VALUE` (46,341 squared), and the extreme seeds.
 */
class SlimeChunksTest {
    @Test
    fun `slime chunks near the origin match cubiomes`() {
        assertThat(slimeChunksIn(262L, -8, -8)).containsExactlyElementsIn(
            chunks(
                -8 to -6, -8 to 2, -7 to -6, -7 to -4, -7 to 2, -7 to 5, -6 to -8, -5 to 7, -4 to -7, -4 to -5,
                -4 to -1, -3 to -5, -3 to 7, -2 to -3, -2 to 3, -2 to 6, -1 to -7, -1 to 1, 0 to -7, 0 to -3,
                3 to -5, 3 to 0, 3 to 7, 4 to -8, 4 to 6, 5 to 0, 5 to 4, 7 to -5, 7 to 3, 7 to 5,
            ),
        )
    }

    @Test
    fun `a negative seed matches cubiomes`() {
        assertThat(slimeChunksIn(-4172144997902289642L, 100, -60)).containsExactlyElementsIn(
            chunks(
                100 to -59, 101 to -46, 102 to -60, 102 to -52, 102 to -49, 102 to -48, 103 to -55, 103 to -48,
                103 to -47, 106 to -51, 106 to -47, 106 to -45, 107 to -58, 107 to -53, 108 to -60, 109 to -53,
                110 to -54, 110 to -49, 111 to -60, 111 to -50, 112 to -52, 112 to -49, 113 to -59, 114 to -57,
                114 to -45,
            ),
        )
    }

    @Test
    fun `the world border with the largest seed matches cubiomes`() {
        assertThat(slimeChunksIn(Long.MAX_VALUE, -1875008, 1874992)).containsExactlyElementsIn(
            chunks(
                -1875008 to 1875006, -1875007 to 1874996, -1875007 to 1875001, -1875005 to 1874993,
                -1875004 to 1875001, -1875003 to 1875006, -1875002 to 1874995, -1875002 to 1874996,
                -1875002 to 1875002, -1875001 to 1874993, -1875001 to 1874999, -1875001 to 1875004,
                -1875000 to 1875000, -1875000 to 1875004, -1875000 to 1875007, -1874999 to 1874993,
                -1874999 to 1875000, -1874998 to 1875004, -1874997 to 1874996, -1874996 to 1874996,
                -1874996 to 1874999, -1874996 to 1875001, -1874994 to 1874994,
            ),
        )
    }

    @Test
    fun `squares that overflow an int match cubiomes`() {
        assertThat(slimeChunksIn(Long.MIN_VALUE, 46336, -46346)).containsExactlyElementsIn(
            chunks(
                46336 to -46342, 46338 to -46342, 46338 to -46334, 46338 to -46333, 46338 to -46332, 46339 to -46342,
                46339 to -46333, 46341 to -46342, 46342 to -46344, 46343 to -46342, 46343 to -46336, 46343 to -46332,
                46344 to -46333, 46345 to -46341, 46345 to -46339, 46345 to -46335, 46346 to -46332, 46347 to -46338,
                46347 to -46331, 46349 to -46336, 46351 to -46337,
            ),
        )
    }

    @Test
    fun `a region lists its chunks row by row`() {
        val region = SlimeChunks.region(262L, -1, -1)
        assertThat(region).hasLength(SlimeChunks.REGION_CHUNKS * SlimeChunks.REGION_CHUNKS)
        // Chunk (-7, -6) is the 25th column and 26th row of region (-1, -1), which starts at chunk (-32, -32).
        assertThat(region[26 * SlimeChunks.REGION_CHUNKS + 25]).isTrue()
        assertThat(region.count { it }).isEqualTo(REGION_MINUS_ONE_COUNT)
        assertThat(SlimeChunks.region(262L, 0, 0).count { it }).isEqualTo(REGION_ZERO_COUNT)
    }

    private fun slimeChunksIn(
        seed: Long,
        x0: Int,
        z0: Int,
    ): List<Pair<Int, Int>> =
        (x0 until x0 + WINDOW).flatMap { x ->
            (z0 until z0 + WINDOW).filter { z -> SlimeChunks.isSlimeChunk(seed, x, z) }.map { z -> x to z }
        }

    private fun chunks(vararg chunks: Pair<Int, Int>): List<Pair<Int, Int>> = chunks.toList()

    private companion object {
        const val WINDOW = 16

        // Counted by the same host build over chunks -32..-1 and 0..31 on both axes.
        const val REGION_MINUS_ONE_COUNT = 103
        const val REGION_ZERO_COUNT = 111
    }
}
