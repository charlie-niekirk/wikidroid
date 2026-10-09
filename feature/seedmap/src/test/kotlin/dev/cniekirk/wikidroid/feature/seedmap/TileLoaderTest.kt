package dev.cniekirk.wikidroid.feature.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.BiomeTile
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.TILE_CELLS
import dev.cniekirk.wikidroid.core.seedmap.TileCache
import dev.cniekirk.wikidroid.core.seedmap.TileKey
import dev.cniekirk.wikidroid.core.seedmap.TileRenderer
import dev.cniekirk.wikidroid.core.seedmap.TileScale
import dev.cniekirk.wikidroid.core.testing.RobolectricTest
import dev.cniekirk.wikidroid.core.testing.fake.FakeSeedMapEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TileLoaderTest : RobolectricTest() {
    private val engine = FakeSeedMapEngine()
    private val cache = TileCache(engine, TileRenderer(engine), maxBytes = 64 * TILE_CELLS * TILE_CELLS * 4)
    private val world = MapWorld(262, McVersion.newest)

    private fun key(
        x: Int,
        z: Int = 0,
    ) = TileKey(world, TileScale.CELL_16, x, z)

    private var loaded = 0

    private fun TestScope.loader() = TileLoader(this, cache, StandardTestDispatcher(testScheduler)) { loaded++ }

    @Test
    fun loadsEveryWantedTileAndSaysSo() =
        runTest {
            val loader = loader()

            loader.request(listOf(key(0), key(1), key(2)))
            runCurrent()

            listOf(key(0), key(1), key(2)).forEach { assertThat(cache.peek(it)).isNotNull() }
            assertThat(loaded).isEqualTo(3)
            assertThat(loader.pending).isEmpty()
        }

    @Test
    fun doesNotGenerateATileTheCacheAlreadyHas() =
        runTest {
            val loader = loader()
            loader.request(listOf(key(0)))
            runCurrent()
            engine.tileRequests.clear()

            loader.request(listOf(key(0)))
            runCurrent()

            assertThat(engine.tileRequests).isEmpty()
        }

    @Test
    fun doesNotAskTwiceForATileThatIsStillLoading() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            engine.tileHandler = { gate.await().let { _ -> BiomeTile(it, IntArray(TILE_CELLS * TILE_CELLS)) } }
            val loader = loader()

            loader.request(listOf(key(0)))
            runCurrent()
            loader.request(listOf(key(0), key(1)))
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertThat(engine.tileRequests.count { it == key(0) }).isEqualTo(1)
            assertThat(cache.peek(key(0))).isNotNull()
            assertThat(cache.peek(key(1))).isNotNull()
        }

    @Test
    fun stopsLoadingTilesThatScrolledOutOfView() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            engine.tileHandler = { gate.await().let { _ -> BiomeTile(it, IntArray(TILE_CELLS * TILE_CELLS)) } }
            val loader = loader()

            loader.request(listOf(key(0), key(1)))
            runCurrent()
            loader.request(listOf(key(1)))
            assertThat(loader.pending).containsExactly(key(1))
            gate.complete(Unit)
            runCurrent()

            assertThat(cache.peek(key(0))).isNull()
            assertThat(cache.peek(key(1))).isNotNull()
            assertThat(loaded).isEqualTo(1)
        }

    @Test
    fun aTileThatScrolledBackIsLoadedAgain() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            engine.tileHandler = { gate.await().let { _ -> BiomeTile(it, IntArray(TILE_CELLS * TILE_CELLS)) } }
            val loader = loader()
            loader.request(listOf(key(0)))
            runCurrent()
            loader.request(listOf(key(5)))
            loader.request(listOf(key(0)))
            gate.complete(Unit)
            runCurrent()

            assertThat(cache.peek(key(0))).isNotNull()
            assertThat(loader.pending).isEmpty()
        }

    @Test
    fun aTileTheEngineRefusesIsNotRetried() =
        runTest {
            engine.tileHandler = { error("past the border") }
            val loader = loader()

            loader.request(listOf(key(0)))
            runCurrent()
            loader.request(listOf(key(0), key(1)))
            runCurrent()

            assertThat(engine.tileRequests.count { it == key(0) }).isEqualTo(1)
            assertThat(loaded).isEqualTo(0)
            assertThat(loader.pending).isEmpty()
        }

    @Test
    fun cancelAllStopsEverything() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            engine.tileHandler = { gate.await().let { _ -> BiomeTile(it, IntArray(TILE_CELLS * TILE_CELLS)) } }
            val loader = loader()
            loader.request(listOf(key(0), key(1)))
            runCurrent()

            loader.cancelAll()
            gate.complete(Unit)
            runCurrent()

            assertThat(loader.pending).isEmpty()
            assertThat(loaded).isEqualTo(0)
        }

    @Test
    fun cancelAllIsSafeWhenCancelledJobsFinishImmediately() {
        // Compose's main dispatcher runs a cancelled job's cleanup inside cancel(), like Unconfined does here.
        val gate = CompletableDeferred<Unit>()
        engine.tileHandler = { gate.await().let { _ -> BiomeTile(it, IntArray(TILE_CELLS * TILE_CELLS)) } }
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val loader = TileLoader(scope, cache, Dispatchers.Default) { loaded++ }
        loader.request(listOf(key(0), key(1), key(2)))

        loader.cancelAll()

        assertThat(loader.pending).isEmpty()
        gate.complete(Unit)
        scope.cancel()
    }
}
