package dev.cniekirk.wikidroid.core.seedmap

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class TilesTest {
    private val world = MapWorld(seed = 262, version = McVersion.V1_18)

    @Test
    fun `a tile covers 256 cells of its scale`() {
        assertThat(TileScale.CELL_4.tileSpan).isEqualTo(1024)
        assertThat(TileScale.CELL_16.tileSpan).isEqualTo(4096)
        assertThat(TileScale.CELL_64.tileSpan).isEqualTo(16_384)
        assertThat(TileScale.CELL_256.tileSpan).isEqualTo(65_536)
    }

    @Test
    fun `tile areas tile the plane without gaps`() {
        val key = TileKey(world, TileScale.CELL_16, tileX = 2, tileZ = -1)

        assertThat(key.area).isEqualTo(BlockArea(8192, -4096, 12_288, 0))
        assertThat(TileKey(world, TileScale.CELL_16, 3, -1).area.minX).isEqualTo(key.area.maxX)
        assertThat(TileKey(world, TileScale.CELL_16, 2, 0).area.minZ).isEqualTo(key.area.maxZ)
    }

    @Test
    fun `the scale is the coarsest whose cells fit in a pixel`() {
        assertThat(TileScale.forBlocksPerPixel(0.25f)).isEqualTo(TileScale.CELL_4)
        assertThat(TileScale.forBlocksPerPixel(1f)).isEqualTo(TileScale.CELL_4)
        assertThat(TileScale.forBlocksPerPixel(4f)).isEqualTo(TileScale.CELL_4)
        assertThat(TileScale.forBlocksPerPixel(15.9f)).isEqualTo(TileScale.CELL_4)
        assertThat(TileScale.forBlocksPerPixel(16f)).isEqualTo(TileScale.CELL_16)
        assertThat(TileScale.forBlocksPerPixel(70f)).isEqualTo(TileScale.CELL_64)
        assertThat(TileScale.forBlocksPerPixel(256f)).isEqualTo(TileScale.CELL_256)
        assertThat(TileScale.forBlocksPerPixel(10_000f)).isEqualTo(TileScale.CELL_256)
    }

    @Test
    fun `covering lists the tiles an area touches, row by row`() {
        val tiles = TileKey.covering(world, TileScale.CELL_4, BlockArea(-10, -10, 1030, 10))

        assertThat(tiles.map { it.tileX to it.tileZ })
            .containsExactly(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 0 to 0, 1 to 0)
            .inOrder()
    }

    @Test
    fun `an area ending exactly on a tile edge does not pull in the next tile`() {
        val tiles = TileKey.covering(world, TileScale.CELL_4, BlockArea(0, 0, 1024, 1024))

        assertThat(tiles).containsExactly(TileKey(world, TileScale.CELL_4, 0, 0))
    }

    @Test
    fun `a one block area still needs its tile`() {
        val tiles = TileKey.covering(world, TileScale.CELL_64, BlockArea(-1, -1, 0, 0))

        assertThat(tiles).containsExactly(TileKey(world, TileScale.CELL_64, -1, -1))
    }

    @Test
    fun `tiles past the world border are refused`() {
        assertThrows(IllegalArgumentException::class.java) { TileKey(world, TileScale.CELL_4, 40_000, 0) }
        assertThrows(IllegalArgumentException::class.java) { TileKey(world, TileScale.CELL_256, 0, -1000) }
    }

    @Test
    fun `the tiles at the border are accepted and their area fits an Int`() {
        val edge = SeedMapEngine.MAX_COORDINATE / TileScale.CELL_4.tileSpan + 1

        val key = TileKey(world, TileScale.CELL_4, edge, -edge)

        assertThat(key.area.maxX).isGreaterThan(SeedMapEngine.MAX_COORDINATE)
    }

    @Test
    fun `a biome tile must have a full grid of cells`() {
        val key = TileKey(world, TileScale.CELL_4, 0, 0)

        assertThat(BiomeTile(key, IntArray(TILE_CELLS * TILE_CELLS)).biomes).hasLength(65_536)
        assertThrows(IllegalArgumentException::class.java) { BiomeTile(key, IntArray(10)) }
    }
}
