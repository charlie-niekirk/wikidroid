package dev.cniekirk.wikidroid.feature.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.cniekirk.wikidroid.core.seedmap.TileKey
import dev.cniekirk.wikidroid.core.seedmap.TileScale
import org.junit.Test

class TileLayoutTest {
    private val world = MapWorld(262, McVersion.V1_21_1)

    @Test
    fun theScaleFollowsTheZoomWithTwoPixelsOfSlack() {
        assertThat(tileScaleFor(0.25f)).isEqualTo(TileScale.CELL_4)
        assertThat(tileScaleFor(1f)).isEqualTo(TileScale.CELL_4)
        assertThat(tileScaleFor(2f)).isEqualTo(TileScale.CELL_4)
        // 16-block cells are 2 pixels wide at 8 blocks per pixel.
        assertThat(tileScaleFor(8f)).isEqualTo(TileScale.CELL_16)
        assertThat(tileScaleFor(31f)).isEqualTo(TileScale.CELL_16)
        assertThat(tileScaleFor(32f)).isEqualTo(TileScale.CELL_64)
        assertThat(tileScaleFor(128f)).isEqualTo(TileScale.CELL_256)
        assertThat(tileScaleFor(MapCamera.MAX_BLOCKS_PER_PIXEL)).isEqualTo(TileScale.CELL_256)
    }

    @Test
    fun noTilesUntilTheCanvasHasASize() {
        assertThat(wantedTiles(MapCamera(), world)).isEmpty()
    }

    @Test
    fun wantsEveryTileOnScreenAtTheScaleOfTheZoom() {
        val camera = MapCamera(blocksPerPixel = 1f).apply { resize(512, 512) }

        val wanted = wantedTiles(camera, world)

        // 512 blocks across at 4 blocks per cell: tiles are 1024 blocks, so the origin's four neighbours.
        assertThat(wanted.map { it.scale }.toSet()).containsExactly(TileScale.CELL_4)
        assertThat(wanted.map { it.tileX to it.tileZ })
            .containsExactly(-1 to -1, 0 to -1, -1 to 0, 0 to 0)
    }

    @Test
    fun nearestTilesComeFirst() {
        val camera = MapCamera(centerX = 900.0, centerZ = 100.0, blocksPerPixel = 1f).apply { resize(2500, 200) }

        val wanted = wantedTiles(camera, world)

        // The camera sits over tile (0, 0); tile (1, 0) is next, then the far ones.
        assertThat(wanted.first().let { it.tileX to it.tileZ }).isEqualTo(0 to 0)
        val distances =
            wanted.map { key ->
                val area = key.area
                val dx = (area.minX + area.maxX) / 2.0 - camera.centerX
                val dz = (area.minZ + area.maxZ) / 2.0 - camera.centerZ
                dx * dx + dz * dz
            }
        assertThat(distances).isInOrder()
    }

    @Test
    fun neverWantsMoreThanTheLimit() {
        val camera = MapCamera(blocksPerPixel = 2f).apply { resize(6000, 6000) }

        assertThat(wantedTiles(camera, world)).hasSize(MAX_WANTED_TILES)
    }

    @Test
    fun tilesAtTheEdgeOfTheWorldAreValidKeys() {
        val limit = SeedMapEngine.MAX_COORDINATE.toDouble()
        val camera = MapCamera(centerX = limit, centerZ = -limit, blocksPerPixel = 1f).apply { resize(800, 800) }

        val wanted = wantedTiles(camera, world)

        assertThat(wanted).isNotEmpty()
        wanted.forEach { assertThat(isTileIndexInBorder(it.scale, it.tileX)).isTrue() }
    }

    @Test
    fun tileIndicesAreInBorderOnlyUpToOnePastTheEdge() {
        val last = SeedMapEngine.MAX_COORDINATE / TileScale.CELL_4.tileSpan + 1

        assertThat(isTileIndexInBorder(TileScale.CELL_4, last)).isTrue()
        assertThat(isTileIndexInBorder(TileScale.CELL_4, -last)).isTrue()
        assertThat(isTileIndexInBorder(TileScale.CELL_4, last + 1)).isFalse()
        // The check agrees with TileKey, which throws for the first index it refuses.
        TileKey(world, TileScale.CELL_4, last, last)
    }
}
