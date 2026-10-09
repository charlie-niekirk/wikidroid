package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.StructurePos
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import org.junit.Test

class MarkerHitTest {
    /** One block per pixel with block (0, 0) at screen (500, 1000). */
    private val camera = MapCamera(blocksPerPixel = 1f).apply { resize(1000, 2000) }

    private val village = StructurePos(StructureType.VILLAGE, BlockPos(100, 100))
    private val temple = StructurePos(StructureType.DESERT_PYRAMID, BlockPos(110, 100))

    private fun hit(
        tapX: Float,
        tapY: Float,
        spawn: BlockPos? = null,
        strongholds: List<BlockPos> = emptyList(),
        pins: List<StructurePos> = emptyList(),
        radius: Float = 20f,
    ) = hitTest(camera, Offset(tapX, tapY), spawn, strongholds, pins, radius)

    @Test
    fun aTapOnBareMapHitsNothing() {
        assertThat(hit(0f, 0f, pins = listOf(village))).isNull()
    }

    @Test
    fun aTapNearAPinSelectsIt() {
        // The village is drawn at screen (600, 1100).
        assertThat(hit(610f, 1090f, pins = listOf(village))).isEqualTo(MarkerHit.Structure(village))
    }

    @Test
    fun aTapJustOutsideTheRadiusMisses() {
        assertThat(hit(621f, 1100f, pins = listOf(village))).isNull()
        assertThat(hit(619f, 1100f, pins = listOf(village))).isEqualTo(MarkerHit.Structure(village))
    }

    @Test
    fun theNearerOfTwoOverlappingPinsWins() {
        val pins = listOf(village, temple)

        assertThat(hit(601f, 1100f, pins = pins)).isEqualTo(MarkerHit.Structure(village))
        assertThat(hit(609f, 1100f, pins = pins)).isEqualTo(MarkerHit.Structure(temple))
    }

    @Test
    fun spawnAndStrongholdsCanBeTapped() {
        assertThat(hit(500f, 1000f, spawn = BlockPos(0, 0))).isEqualTo(MarkerHit.Spawn)
        assertThat(hit(500f, 1000f, strongholds = listOf(BlockPos(0, 0))))
            .isEqualTo(MarkerHit.Structure(StructurePos(StructureType.STRONGHOLD, BlockPos(0, 0))))
    }

    @Test
    fun onATieTheSpawnBeatsStrongholdsWhichBeatPins() {
        val here = BlockPos(0, 0)
        val pin = StructurePos(StructureType.VILLAGE, here)

        assertThat(
            hit(500f, 1000f, spawn = here, strongholds = listOf(here), pins = listOf(pin)),
        ).isEqualTo(MarkerHit.Spawn)
        assertThat(hit(500f, 1000f, strongholds = listOf(here), pins = listOf(pin)))
            .isEqualTo(MarkerHit.Structure(StructurePos(StructureType.STRONGHOLD, here)))
    }

    @Test
    fun theRadiusIsInScreenPixelsWhateverTheZoom() {
        camera.zoomBy(0.25f) // 4 blocks per pixel: the village is now 25 pixels from the middle.

        assertThat(hit(525f, 1025f, pins = listOf(village))).isEqualTo(MarkerHit.Structure(village))
        assertThat(hit(600f, 1100f, pins = listOf(village))).isNull()
    }

    @Test
    fun aTapOnBareMapSelectsTheBiomeUnderIt() {
        val state = SeedMapState()

        val action = selectionAt(camera, Offset(520f, 1030f), state, radiusPx = 20f)

        assertThat(action).isEqualTo(SeedMapAction.SelectPoint(BlockPos(20, 30)))
    }

    @Test
    fun aTapOnAPinSelectsTheStructure() {
        val state = SeedMapState(pins = kotlinx.collections.immutable.persistentListOf(village))

        val action = selectionAt(camera, Offset(600f, 1100f), state, radiusPx = 20f)

        assertThat(action).isEqualTo(SeedMapAction.SelectStructure(village))
    }
}
