package dev.cniekirk.wikidroid.feature.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import org.junit.Assert.assertThrows
import org.junit.Test

class MapViewportTest {
    private fun viewport(
        centerX: Int = 0,
        centerZ: Int = 0,
        blocksPerPixel: Float = 1f,
        widthPx: Int = 1000,
        heightPx: Int = 2000,
    ) = MapViewport(centerX, centerZ, blocksPerPixel, widthPx, heightPx)

    @Test
    fun `the visible area is centred on the viewport`() {
        assertThat(viewport(centerX = 100, centerZ = -50).visibleArea).isEqualTo(BlockArea(-400, -1050, 600, 950))
    }

    @Test
    fun `zooming out shows more blocks`() {
        val near = viewport(blocksPerPixel = 1f).visibleArea
        val far = viewport(blocksPerPixel = 4f).visibleArea

        assertThat(far.width).isEqualTo(near.width * 4)
        assertThat(far.height).isEqualTo(near.height * 4)
    }

    @Test
    fun `zooming in past one block per pixel still gives a non empty area`() {
        val area = viewport(blocksPerPixel = 0.01f, widthPx = 1, heightPx = 1).visibleArea

        assertThat(area.width).isAtLeast(1)
        assertThat(area.height).isAtLeast(1)
    }

    @Test
    fun `the visible area stops at the world border`() {
        val limit = SeedMapEngine.MAX_COORDINATE

        val area = viewport(centerX = limit, centerZ = -limit, blocksPerPixel = 64f).visibleArea

        assertThat(area.maxX).isEqualTo(limit)
        assertThat(area.minZ).isEqualTo(-limit)
        assertThat(area.width).isGreaterThan(0)
    }

    @Test
    fun `a viewport far outside the border still yields an area`() {
        val area = viewport(centerX = Int.MAX_VALUE, centerZ = Int.MIN_VALUE, blocksPerPixel = 1000f).visibleArea

        assertThat(area.width).isAtLeast(1)
        assertThat(area.maxX).isAtMost(SeedMapEngine.MAX_COORDINATE)
        assertThat(area.minZ).isAtLeast(-SeedMapEngine.MAX_COORDINATE)
    }

    @Test
    fun `a viewport needs a size and a positive scale`() {
        assertThrows(IllegalArgumentException::class.java) { viewport(blocksPerPixel = 0f) }
        assertThrows(IllegalArgumentException::class.java) { viewport(blocksPerPixel = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { viewport(widthPx = 0) }
    }

    @Test
    fun `the pin search covers the screen with padding, snapped outward to the grid`() {
        // Screen: x -500..500, z -1000..1000; padded by 512 and rounded out to 1024.
        assertThat(pinSearchArea(viewport())).isEqualTo(BlockArea(-1024, -2048, 1024, 2048))
    }

    @Test
    fun `small pans ask for the same search area`() {
        val here = pinSearchArea(viewport(centerX = 0))
        val nudged = pinSearchArea(viewport(centerX = 10, centerZ = 5))

        assertThat(nudged).isEqualTo(here)
    }

    @Test
    fun `a pan across a grid line moves the search area`() {
        val here = pinSearchArea(viewport(centerX = 0))
        val far = pinSearchArea(viewport(centerX = 600))

        assertThat(far).isNotEqualTo(here)
    }

    @Test
    fun `the search area always contains what is on screen`() {
        for (center in listOf(-7777, -1, 0, 1, 1023, 1024, 123_456)) {
            val view = viewport(centerX = center, centerZ = -center / 3, blocksPerPixel = 2.5f)
            val search = pinSearchArea(view)!!
            val visible = view.visibleArea

            assertThat(search.minX).isAtMost(visible.minX)
            assertThat(search.minZ).isAtMost(visible.minZ)
            assertThat(search.maxX).isAtLeast(visible.maxX)
            assertThat(search.maxZ).isAtLeast(visible.maxZ)
        }
    }

    @Test
    fun `a view too wide to search has no search area`() {
        assertThat(pinSearchArea(viewport(blocksPerPixel = 16f))).isNull()
        assertThat(pinSearchArea(viewport(blocksPerPixel = 2f))).isNotNull()
    }

    @Test
    fun `the search area never exceeds what the engine accepts`() {
        val span = SeedMapEngine.MAX_STRUCTURE_SPAN
        var widest = 0
        var bpp = 0.25f
        while (bpp < 20f) {
            pinSearchArea(viewport(blocksPerPixel = bpp, widthPx = 1000, heightPx = 1000))?.let {
                widest =
                    maxOf(widest, it.width, it.height)
            }
            bpp += 0.25f
        }

        assertThat(widest).isAtMost(span)
        assertThat(widest).isGreaterThan(0)
    }

    @Test
    fun `at the border the search area stays inside it`() {
        val limit = SeedMapEngine.MAX_COORDINATE
        val area = pinSearchArea(viewport(centerX = limit, centerZ = limit, blocksPerPixel = 1f))!!

        assertThat(area.maxX).isAtMost(limit)
        assertThat(area.maxZ).isAtMost(limit)
    }
}
