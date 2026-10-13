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
                "-8:-6 -8:2 -7:-6 -7:-4 -7:2 -7:5 -6:-8 -5:7 -4:-7 -4:-5 -4:-1 -3:-5 -3:7 -2:-3 -2:3 " +
                    "-2:6 -1:-7 -1:1 0:-7 0:-3 3:-5 3:0 3:7 4:-8 4:6 5:0 5:4 7:-5 7:3 7:5",
            ),
        )
    }

    @Test
    fun `a negative seed matches cubiomes`() {
        assertThat(slimeChunksIn(-4172144997902289642L, 100, -60)).containsExactlyElementsIn(
            chunks(
                "100:-59 101:-46 102:-60 102:-52 102:-49 102:-48 103:-55 103:-48 103:-47 106:-51 106:-47 " +
                    "106:-45 107:-58 107:-53 108:-60 109:-53 110:-54 110:-49 111:-60 111:-50 112:-52 112:-49 " +
                    "113:-59 114:-57 114:-45",
            ),
        )
    }

    @Test
    fun `the world border with the largest seed matches cubiomes`() {
        assertThat(slimeChunksIn(Long.MAX_VALUE, -1875008, 1874992)).containsExactlyElementsIn(
            chunks(
                "-1875008:1875006 -1875007:1874996 -1875007:1875001 -1875005:1874993 -1875004:1875001 " +
                    "-1875003:1875006 -1875002:1874995 -1875002:1874996 -1875002:1875002 -1875001:1874993 " +
                    "-1875001:1874999 -1875001:1875004 -1875000:1875000 -1875000:1875004 -1875000:1875007 " +
                    "-1874999:1874993 -1874999:1875000 -1874998:1875004 -1874997:1874996 -1874996:1874996 " +
                    "-1874996:1874999 -1874996:1875001 -1874994:1874994",
            ),
        )
    }

    @Test
    fun `squares that overflow an int match cubiomes`() {
        assertThat(slimeChunksIn(Long.MIN_VALUE, 46336, -46346)).containsExactlyElementsIn(
            chunks(
                "46336:-46342 46338:-46342 46338:-46334 46338:-46333 46338:-46332 46339:-46342 " +
                    "46339:-46333 46341:-46342 46342:-46344 46343:-46342 46343:-46336 46343:-46332 " +
                    "46344:-46333 46345:-46341 46345:-46339 46345:-46335 46346:-46332 46347:-46338 " +
                    "46347:-46331 46349:-46336 46351:-46337",
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

    /** Chunks written as `x:z`, separated by spaces, the way the host build printed them. */
    private fun chunks(text: String): List<Pair<Int, Int>> =
        text.trim().split(' ').map { chunk ->
            val (x, z) = chunk.split(':')
            x.toInt() to z.toInt()
        }

    private companion object {
        const val WINDOW = 16

        // Counted by the same host build over chunks -32..-1 and 0..31 on both axes.
        const val REGION_MINUS_ONE_COUNT = 103
        const val REGION_ZERO_COUNT = 111
    }
}
