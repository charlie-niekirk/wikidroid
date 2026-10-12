package dev.cniekirk.wikidroid.core.domain.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.testing.fake.FakeSeedMapEngine
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BiomeAtUseCaseTest {
    private val engine = FakeSeedMapEngine()
    private val world = MapWorld(262L, McVersion.newest)

    @Test
    fun looksUpTheBiomeAtTheBlock() =
        runTest {
            assertThat(BiomeAtUseCase(engine)(world, BlockPos(16, -32))).isEqualTo(FakeSeedMapEngine.PLAINS)
            assertThat(engine.biomeRequests).containsExactly(Triple(world, 16, -32))
        }

    @Test
    fun aPositionTheEngineRefusesHasNoBiome() =
        runTest {
            engine.biomeHandler = { _, _, _ -> throw IllegalArgumentException("outside the world border") }

            assertThat(BiomeAtUseCase(engine)(world, BlockPos(0, 0))).isNull()
        }
}
