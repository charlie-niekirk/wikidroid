package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.runtime.saveable.SaverScope
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import org.junit.Test

class MapCameraTest {
    private fun camera(
        centerX: Double = 0.0,
        centerZ: Double = 0.0,
        blocksPerPixel: Float = 4f,
    ) = MapCamera(centerX, centerZ, blocksPerPixel).apply { resize(1000, 2000) }

    @Test
    fun hasNoViewportUntilItHasASize() {
        val camera = MapCamera()

        assertThat(camera.viewport()).isNull()
        assertThat(camera.visibleArea()).isNull()
        camera.resize(100, 200)
        assertThat(camera.viewport()).isEqualTo(MapViewport(0, 0, MapCamera.DEFAULT_BLOCKS_PER_PIXEL, 100, 200))
    }

    @Test
    fun theCentreOfTheCanvasIsTheCentreOfTheCamera() {
        val camera = camera(centerX = 100.0, centerZ = -50.0)

        assertThat(camera.worldX(500f)).isEqualTo(100.0)
        assertThat(camera.worldZ(1000f)).isEqualTo(-50.0)
        assertThat(camera.screenX(100.0)).isEqualTo(500f)
        assertThat(camera.screenY(-50.0)).isEqualTo(1000f)
    }

    @Test
    fun oneScreenPixelIsBlocksPerPixelBlocks() {
        val camera = camera(blocksPerPixel = 4f)

        assertThat(camera.worldX(510f) - camera.worldX(500f)).isEqualTo(40.0)
        assertThat(camera.screenX(40.0) - camera.screenX(0.0)).isEqualTo(10f)
    }

    @Test
    fun draggingMovesTheMapWithTheFinger() {
        val camera = camera(blocksPerPixel = 4f)

        camera.panBy(dxPx = 10f, dyPx = -5f)

        // The map follows the finger right and up, so the camera moves left and down.
        assertThat(camera.centerX).isEqualTo(-40.0)
        assertThat(camera.centerZ).isEqualTo(20.0)
    }

    @Test
    fun aDraggedBlockStaysUnderTheFinger() {
        val camera = camera(centerX = 300.0, centerZ = 120.0, blocksPerPixel = 2f)
        val before = camera.worldX(200f)

        camera.panBy(37f, 0f)

        assertThat(camera.worldX(237f)).isWithin(1e-9).of(before)
    }

    @Test
    fun zoomingKeepsTheFocusedBlockWhereItIs() {
        val camera = camera(centerX = 800.0, centerZ = -300.0, blocksPerPixel = 8f)
        val focusX = 250f
        val focusY = 1700f
        val blockX = camera.worldX(focusX)
        val blockZ = camera.worldZ(focusY)

        camera.zoomBy(2f, focusX, focusY)

        assertThat(camera.blocksPerPixel).isEqualTo(4f)
        assertThat(camera.worldX(focusX)).isWithin(1e-6).of(blockX)
        assertThat(camera.worldZ(focusY)).isWithin(1e-6).of(blockZ)
    }

    @Test
    fun zoomingAroundTheMiddleLeavesTheCentreAlone() {
        val camera = camera(centerX = 800.0, centerZ = -300.0)

        camera.zoomBy(2f)

        assertThat(camera.centerX).isWithin(1e-6).of(800.0)
        assertThat(camera.centerZ).isWithin(1e-6).of(-300.0)
    }

    @Test
    fun zoomStopsAtTheLimits() {
        val camera = camera(blocksPerPixel = 1f)

        camera.zoomBy(1000f)
        assertThat(camera.blocksPerPixel).isEqualTo(MapCamera.MIN_BLOCKS_PER_PIXEL)

        camera.zoomBy(0.0001f)
        assertThat(camera.blocksPerPixel).isEqualTo(MapCamera.MAX_BLOCKS_PER_PIXEL)
    }

    @Test
    fun nonsenseZoomFactorsAreIgnored() {
        val camera = camera(blocksPerPixel = 4f)

        camera.zoomBy(0f)
        camera.zoomBy(-1f)
        camera.zoomBy(Float.NaN)
        camera.zoomBy(Float.POSITIVE_INFINITY)

        assertThat(camera.blocksPerPixel).isEqualTo(4f)
    }

    @Test
    fun theCentreCannotLeaveTheWorld() {
        val limit = SeedMapEngine.MAX_COORDINATE.toDouble()
        val camera = camera(centerX = limit - 10, blocksPerPixel = 4f)

        camera.panBy(-1000f, 0f)
        assertThat(camera.centerX).isEqualTo(limit)

        camera.centerOn(BlockPos(Int.MIN_VALUE, Int.MAX_VALUE))
        assertThat(camera.centerX).isEqualTo(-limit)
        assertThat(camera.centerZ).isEqualTo(limit)
    }

    @Test
    fun centerOnKeepsTheZoom() {
        val camera = camera(blocksPerPixel = 4f)

        camera.centerOn(BlockPos(120, -80))

        assertThat(camera.centerX).isEqualTo(120.0)
        assertThat(camera.centerZ).isEqualTo(-80.0)
        assertThat(camera.blocksPerPixel).isEqualTo(4f)
    }

    @Test
    fun blockAtFloorsAndStaysInsideTheBorder() {
        val camera = camera(centerX = 0.0, centerZ = 0.0, blocksPerPixel = 1f)

        assertThat(camera.blockAt(500f, 1000f)).isEqualTo(BlockPos(0, 0))
        assertThat(camera.blockAt(499.5f, 999.5f)).isEqualTo(BlockPos(-1, -1))
        assertThat(camera.blockAt(1000f, 2000f)).isEqualTo(BlockPos(500, 1000))
    }

    @Test
    fun theVisibleAreaCoversTheCanvasAndIsClippedToTheBorder() {
        val camera = camera(centerX = 100.0, centerZ = 0.0, blocksPerPixel = 2f)

        val area = camera.visibleArea()!!

        assertThat(area.minX).isEqualTo(100 - 1000)
        assertThat(area.maxX).isEqualTo(100 + 1000)
        assertThat(area.minZ).isEqualTo(-2000)
        assertThat(area.maxZ).isEqualTo(2000)

        val limit = SeedMapEngine.MAX_COORDINATE
        val edge = camera(centerX = limit.toDouble(), centerZ = -limit.toDouble()).visibleArea()!!
        assertThat(edge.maxX).isEqualTo(limit)
        assertThat(edge.minZ).isEqualTo(-limit)
    }

    @Test
    fun theViewportRoundsTheCentre() {
        val viewport = camera(centerX = 10.6, centerZ = -10.6, blocksPerPixel = 2f).viewport()!!

        assertThat(viewport.centerX).isEqualTo(11)
        assertThat(viewport.centerZ).isEqualTo(-11)
        assertThat(viewport.blocksPerPixel).isEqualTo(2f)
    }

    @Test
    fun theSaverKeepsPositionAndZoomButNotTheSize() {
        val original = camera(centerX = 12.5, centerZ = -99.0, blocksPerPixel = 16f)

        val saved = with(MapCamera.Saver) { SaverScope { true }.save(original) }!!
        val restored = MapCamera.Saver.restore(saved)!!

        assertThat(restored.centerX).isEqualTo(12.5)
        assertThat(restored.centerZ).isEqualTo(-99.0)
        assertThat(restored.blocksPerPixel).isEqualTo(16f)
        assertThat(restored.hasSize).isFalse()
    }
}
