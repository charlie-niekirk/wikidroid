package dev.cniekirk.wikidroid.core.domain.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.StructurePos
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.testing.fake.FakeSeedMapEngine
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FindStructuresUseCaseTest {
    private val engine = FakeSeedMapEngine()
    private val world = MapWorld(262L, McVersion.newest)
    private val area = BlockArea(-512, -512, 512, 512)

    @Test
    fun searchesEachTypeAndJoinsTheResults() =
        runTest {
            engine.structuresHandler = { _, type, _ -> listOf(StructurePos(type, BlockPos(type.ordinal, 0))) }

            val found = FindStructuresUseCase(engine)(world, setOf(StructureType.VILLAGE, StructureType.IGLOO), area)

            assertThat(found.map { it.type }).containsExactly(StructureType.VILLAGE, StructureType.IGLOO)
            assertThat(engine.structureRequests.map { it.area }.toSet()).containsExactly(area)
        }

    @Test
    fun aTypeTheEngineFailsOnLeavesTheOthers() =
        runTest {
            engine.structuresHandler = { _, type, _ ->
                check(type != StructureType.IGLOO) { "not in this version" }
                listOf(StructurePos(type, BlockPos(0, 0)))
            }

            val found = FindStructuresUseCase(engine)(world, setOf(StructureType.VILLAGE, StructureType.IGLOO), area)

            assertThat(found).containsExactly(StructurePos(StructureType.VILLAGE, BlockPos(0, 0)))
        }
}
