package dev.cniekirk.wikidroid.core.domain.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.cniekirk.wikidroid.core.testing.fake.FakeSeedMapEngine
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FindStrongholdsUseCaseTest {
    private val engine = FakeSeedMapEngine()
    private val world = MapWorld(262L, McVersion.newest)

    @Test
    fun asksForEveryStronghold() =
        runTest {
            engine.strongholdsHandler = { _, _ -> listOf(BlockPos(1200, 300)) }

            assertThat(FindStrongholdsUseCase(engine)(world)).containsExactly(BlockPos(1200, 300))
            assertThat(engine.strongholdRequests).containsExactly(world to SeedMapEngine.MAX_STRONGHOLDS)
        }

    @Test
    fun anEngineFailureIsNoStrongholds() =
        runTest {
            engine.strongholdsHandler = { _, _ -> throw IllegalArgumentException("bad world") }

            assertThat(FindStrongholdsUseCase(engine)(world)).isEmpty()
        }
}
