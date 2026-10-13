package dev.cniekirk.wikidroid.core.domain.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.domain.seedmap.FindNearestStructureUseCase.Companion.CELL_BLOCKS
import dev.cniekirk.wikidroid.core.domain.seedmap.FindNearestStructureUseCase.Companion.MAX_RINGS
import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.cniekirk.wikidroid.core.seedmap.StructurePos
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.testing.fake.FakeSeedMapEngine
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FindNearestStructureUseCaseTest {
    private val engine = FakeSeedMapEngine()
    private val world = MapWorld(262L, McVersion.newest)
    private val nearest = FindNearestStructureUseCase(engine)
    private val origin = BlockPos(0, 0)

    /** The engine knows [villages]; each request gets those inside its area, as the real one does. */
    private fun villagesAt(vararg villages: BlockPos) {
        engine.structuresHandler = { _, type, area ->
            villages.filter { it in area }.map { StructurePos(type, it) }
        }
    }

    private operator fun BlockArea.contains(pos: BlockPos) = pos.x in minX until maxX && pos.z in minZ until maxZ

    @Test
    fun aStructureInTheFirstCellIsFoundWithOneRequest() =
        runTest {
            villagesAt(BlockPos(300, -200))

            val found = nearest(world, StructureType.VILLAGE, origin)

            assertThat(found).isEqualTo(StructurePos(StructureType.VILLAGE, BlockPos(300, -200)))
            assertThat(engine.structureRequests.map { it.area })
                .containsExactly(BlockArea(-CELL_BLOCKS / 2, -CELL_BLOCKS / 2, CELL_BLOCKS / 2, CELL_BLOCKS / 2))
        }

    @Test
    fun theClosestOfSeveralWins() =
        runTest {
            villagesAt(BlockPos(1500, 1500), BlockPos(-400, 100), BlockPos(900, 0))

            assertThat(nearest(world, StructureType.VILLAGE, origin)?.pos).isEqualTo(BlockPos(-400, 100))
        }

    @Test
    fun aHitInTheFirstCellsCornerStillChecksTheNextRing() =
        runTest {
            // The corner of the first cell is further away than its edge, so the ring around it could be closer.
            val corner = BlockPos(2000, 2000)
            val closerOutside = BlockPos(0, 2100)
            villagesAt(corner, closerOutside)

            val found = nearest(world, StructureType.VILLAGE, origin)

            assertThat(found?.pos).isEqualTo(closerOutside)
            assertThat(engine.structureRequests).hasSize(1 + 8)
        }

    @Test
    fun theSearchIsCentredOnThePointAsked() =
        runTest {
            villagesAt(BlockPos(10_100, -20_050))

            val found = nearest(world, StructureType.VILLAGE, BlockPos(10_000, -20_000))

            assertThat(found?.pos).isEqualTo(BlockPos(10_100, -20_050))
            assertThat(engine.structureRequests).hasSize(1)
        }

    @Test
    fun nothingWithinReachIsNullAfterEveryRing() =
        runTest {
            villagesAt(BlockPos(100_000, 0))

            assertThat(nearest(world, StructureType.VILLAGE, origin)).isNull()
            // 1 + 8 + 16 + 24 + 32 cells for five rings.
            assertThat(engine.structureRequests).hasSize((0 until MAX_RINGS).sumOf { if (it == 0) 1 else 8 * it })
        }

    @Test
    fun cellsStopAtTheWorldBorder() =
        runTest {
            val limit = SeedMapEngine.MAX_COORDINATE
            villagesAt()

            nearest(world, StructureType.VILLAGE, BlockPos(limit, limit))

            engine.structureRequests.forEach {
                assertThat(it.area.maxX).isAtMost(limit)
                assertThat(it.area.maxZ).isAtMost(limit)
            }
            // Only the quarter of each ring inside the border is asked for.
            assertThat(engine.structureRequests).hasSize(MAX_RINGS * MAX_RINGS)
        }

    @Test
    fun aCellTheEngineFailsOnCountsAsEmpty() =
        runTest {
            engine.structuresHandler = { _, type, area ->
                check(area.minX < 0) { "boom" }
                if (BlockPos(-2500, 0) in area) listOf(StructurePos(type, BlockPos(-2500, 0))) else emptyList()
            }

            assertThat(nearest(world, StructureType.VILLAGE, origin)?.pos).isEqualTo(BlockPos(-2500, 0))
        }

    @Test
    fun strongholdsComeFromTheEnginesList() =
        runTest {
            engine.strongholdsHandler = { _, _ -> listOf(BlockPos(1800, 0), BlockPos(-600, -900), BlockPos(0, 2200)) }

            val found = nearest(world, StructureType.STRONGHOLD, BlockPos(-100, -100))

            assertThat(found).isEqualTo(StructurePos(StructureType.STRONGHOLD, BlockPos(-600, -900)))
            assertThat(engine.structureRequests).isEmpty()
        }

    @Test
    fun aTypeThisWorldCannotHaveIsNotSearched() =
        runTest {
            villagesAt(BlockPos(0, 0))

            assertThat(nearest(world.copy(dimension = Dimension.NETHER), StructureType.VILLAGE, origin)).isNull()
            assertThat(nearest(world.copy(version = McVersion.V1_12), StructureType.TRIAL_CHAMBERS, origin)).isNull()
            assertThat(engine.structureRequests).isEmpty()
        }
}
