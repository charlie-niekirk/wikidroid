package dev.cniekirk.wikidroid.core.seedmap

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BiomePaletteTest {
    private val colors = IntArray(4) { 0xFF000000.toInt() or (it * 0x10101) }
    private val palette = BiomePalette(colors)

    @Test
    fun `a biome gets the colour at its id`() {
        assertThat(palette.argb(0)).isEqualTo(0xFF000000.toInt())
        assertThat(palette.argb(2)).isEqualTo(0xFF020202.toInt())
    }

    @Test
    fun `ids outside the table are the unknown colour`() {
        assertThat(palette.argb(-1)).isEqualTo(BiomePalette.UNKNOWN)
        assertThat(palette.argb(4)).isEqualTo(BiomePalette.UNKNOWN)
        assertThat(palette.argb(1000)).isEqualTo(BiomePalette.UNKNOWN)
    }

    @Test
    fun `painting maps every cell in order`() {
        assertThat(palette.paint(intArrayOf(3, 1, -1, 0)))
            .asList()
            .containsExactly(0xFF030303.toInt(), 0xFF010101.toInt(), BiomePalette.UNKNOWN, 0xFF000000.toInt())
            .inOrder()
    }

    @Test
    fun `changing the array it was built from does not change the palette`() {
        val source = intArrayOf(0xFF112233.toInt())
        val copy = BiomePalette(source)

        source[0] = 0

        assertThat(copy.argb(0)).isEqualTo(0xFF112233.toInt())
    }
}
