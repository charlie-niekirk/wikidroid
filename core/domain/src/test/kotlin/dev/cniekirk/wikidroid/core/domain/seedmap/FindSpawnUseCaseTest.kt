package dev.cniekirk.wikidroid.core.domain.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.testing.fake.FakeSeedMapEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class FindSpawnUseCaseTest {
    private val engine = FakeSeedMapEngine()
    private val findSpawn = FindSpawnUseCase(engine)
    private val overworld = MapWorld(262L, McVersion.newest)

    @Test
    fun findsTheOverworldSpawn() =
        runTest {
            engine.spawnHandler = { BlockPos(40, -16) }

            assertThat(findSpawn(overworld)).isEqualTo(BlockPos(40, -16))
        }

    @Test
    fun theNetherHasNoSpawnAndTheEngineIsNotAsked() =
        runTest {
            assertThat(findSpawn(overworld.copy(dimension = Dimension.NETHER))).isNull()
            assertThat(engine.spawnRequests).isEmpty()
        }

    @Test
    fun anEngineFailureIsNoSpawn() =
        runTest {
            engine.spawnHandler = { error("generator busy") }

            assertThat(findSpawn(overworld)).isNull()
        }

    @Test
    fun cancellationIsNotSwallowed() {
        engine.spawnHandler = { throw CancellationException("left the map") }

        assertThrows(CancellationException::class.java) { runBlocking { findSpawn(overworld) } }
    }
}
