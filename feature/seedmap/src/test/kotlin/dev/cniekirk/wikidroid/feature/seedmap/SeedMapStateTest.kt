package dev.cniekirk.wikidroid.feature.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.StructurePos
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class SeedMapStateTest {
    private val nether = SeedMapState(dimension = Dimension.NETHER)

    @Test
    fun aVersionWithoutTheDimensionFallsBackToTheOverworld() {
        val older = nether.withVersion(McVersion.V1_1)

        assertThat(older.version).isEqualTo(McVersion.V1_1)
        assertThat(older.dimension).isEqualTo(Dimension.OVERWORLD)
    }

    @Test
    fun aDimensionTheVersionLacksIsIgnored() {
        val older = SeedMapState(version = McVersion.V1_1)

        assertThat(older.withDimension(Dimension.NETHER)).isEqualTo(older)
    }

    @Test
    fun anotherWorldDropsWhatBelongedToTheOldOne() {
        val shown =
            SeedMapState(
                spawn = BlockPos(0, 0),
                strongholds = persistentListOf(BlockPos(1200, 0)),
                selection = MapSelection.Spawn(BlockPos(0, 0)),
            )

        val moved = shown.inWorld(seed = 1L)

        assertThat(moved.spawn).isNull()
        assertThat(moved.strongholds).isEmpty()
        assertThat(moved.selection).isNull()
        assertThat(shown.inWorld()).isSameInstanceAs(shown)
    }

    @Test
    fun answersForAnEarlierWorldAreDiscarded() {
        val state = SeedMapState()
        val earlier = state.world.copy(seed = 1L)

        assertThat(state.withSpawn(earlier, BlockPos(5, 5))).isEqualTo(state)
        assertThat(state.withStrongholds(earlier, listOf(BlockPos(5, 5)))).isEqualTo(state)
        assertThat(state.withBiomeSelection(earlier, BlockPos(5, 5), biome = 1)).isEqualTo(state)
    }

    @Test
    fun pinsOfATypeSwitchedOffWhileSearchingDoNotComeBack() {
        val state = SeedMapState(enabledStructures = SeedMapState.DEFAULT_STRUCTURES)
        val query =
            PinQuery(
                state.world,
                setOf(StructureType.VILLAGE, StructureType.IGLOO),
                BlockArea(-512, -512, 512, 512),
                hidden = false,
            )
        val village = StructurePos(StructureType.VILLAGE, BlockPos(1, 1))
        val igloo = StructurePos(StructureType.IGLOO, BlockPos(2, 2))

        assertThat(state.withPins(query, listOf(village, igloo)).pins).containsExactly(village)
    }

    @Test
    fun aViewTooWideToSearchHidesThePins() {
        val state = SeedMapState(pins = persistentListOf(StructurePos(StructureType.VILLAGE, BlockPos(1, 1))))
        val tooWide = PinQuery(state.world, setOf(StructureType.VILLAGE), area = null, hidden = true)

        val hidden = state.withPins(tooWide, emptyList())

        assertThat(hidden.pins).isEmpty()
        assertThat(hidden.pinsHidden).isTrue()
    }

    @Test
    fun slimeChunksAreOnlyShownInTheOverworld() {
        val on = SeedMapState(showSlimeChunks = true)

        assertThat(on.slimeChunksShown).isTrue()
        assertThat(on.withDimension(Dimension.NETHER).slimeChunksShown).isFalse()
        assertThat(SeedMapState().slimeChunksShown).isFalse()
    }
}
