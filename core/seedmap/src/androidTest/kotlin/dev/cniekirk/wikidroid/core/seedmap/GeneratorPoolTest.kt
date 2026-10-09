package dev.cniekirk.wikidroid.core.seedmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class GeneratorPoolTest {
    private val pools = mutableListOf<GeneratorPool>()

    private fun pool(size: Int) = GeneratorPool(Dispatchers.Default, size).also { pools += it }

    @After
    fun tearDown() = runBlocking { pools.forEach { it.shutDown() } }

    private val worlds =
        listOf(
            MapWorld(262, McVersion.V1_18),
            MapWorld(262, McVersion.V1_12),
            MapWorld(-4172144997902289642L, McVersion.V26_3),
            MapWorld(1, McVersion.V26_3, Dimension.NETHER),
            MapWorld(1, McVersion.V1_21_4, Dimension.END),
        )

    private fun tileOf(
        handle: Long,
        tileX: Int,
        tileZ: Int,
    ): IntArray =
        checkNotNull(
            NativeSeedMap.nativeGenBiomes(
                handle,
                scale = 4,
                x = tileX * TILE_CELLS,
                z = tileZ * TILE_CELLS,
                width = TILE_CELLS,
                height = TILE_CELLS,
                y = 15,
            ),
        )

    @Test
    fun parallelTilesEqualTilesMadeOneAtATime() =
        runBlocking {
            val requests = (0 until 40).map { worlds[it % worlds.size] to (it / worlds.size) }
            val sequential = pool(1)
            val expected = requests.map { (world, tile) -> sequential.use(world) { tileOf(it, tile, -tile) }.toList() }

            val parallel = pool(4)
            val actual =
                requests
                    .map { (world, tile) -> async { parallel.use(world) { tileOf(it, tile, -tile) }.toList() } }
                    .awaitAll()

            assertThat(actual).isEqualTo(expected)
        }

    @Test
    fun aGeneratorIsNeverLentToTwoCallersAtOnceAndNeverMoreThanTheSizeAtOnce() =
        runBlocking {
            val pool = pool(3)
            val lent = ConcurrentHashMap.newKeySet<Long>()
            val running = AtomicInteger()
            var highWater = 0
            val lock = Any()

            (0 until 60)
                .map { index ->
                    async {
                        pool.use(worlds[index % worlds.size]) { handle ->
                            assertThat(lent.add(handle)).isTrue()
                            val now = running.incrementAndGet()
                            synchronized(lock) { highWater = maxOf(highWater, now) }
                            tileOf(handle, index, 0)
                            running.decrementAndGet()
                            lent.remove(handle)
                        }
                    }
                }.awaitAll()

            assertThat(highWater).isAtMost(3)
            assertThat(highWater).isAtLeast(1)
        }

    @Test
    fun switchingWorldsAndVersionsBackAndForthGivesTheSameAnswers() =
        runBlocking {
            val pool = pool(1)
            val first = worlds.map { world -> pool.use(world) { tileOf(it, 3, 3).toList() } }

            val again = worlds.reversed().map { world -> pool.use(world) { tileOf(it, 3, 3).toList() } }.reversed()
            val third = worlds.map { world -> pool.use(world) { tileOf(it, 3, 3).toList() } }

            assertThat(again).isEqualTo(first)
            assertThat(third).isEqualTo(first)
            assertThat(first.toSet()).hasSize(worlds.size)
        }

    @Test
    fun aGeneratorAlreadyOnTheRightWorldIsPreferred() =
        runBlocking {
            val pool = pool(2)

            suspend fun handleFor(world: MapWorld) = pool.use(world) { it }

            // Two callers at once need two generators, one set up for each world.
            val bothHeld = CountDownLatch(2)
            val held =
                listOf(worlds[0], worlds[1])
                    .map { world ->
                        async {
                            pool.use(world) { handle ->
                                bothHeld.countDown()
                                val bothArrived = bothHeld.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                                check(bothArrived) { "the second caller never got a generator" }
                                handle
                            }
                        }
                    }.awaitAll()
            val (a, b) = held

            assertThat(a).isNotEqualTo(b)
            // Idle again, each world finds the generator that is already set up for it.
            assertThat(handleFor(worlds[0])).isEqualTo(a)
            assertThat(handleFor(worlds[1])).isEqualTo(b)
            assertThat(handleFor(worlds[0])).isEqualTo(a)
        }

    @Test
    fun oneCallerAfterAnotherReusesTheSameGenerator() =
        runBlocking {
            val pool = pool(4)

            val handles = worlds.map { world -> pool.use(world) { it } }

            // Without overlap one generator is enough, so no more are created or seeded than needed.
            assertThat(handles.toSet()).hasSize(1)
        }

    @Test
    fun cancelledCallersGiveTheirGeneratorBack() =
        runBlocking {
            val pool = pool(1)
            val holding = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val holder =
                launch {
                    pool.use(worlds[0]) {
                        holding.complete(Unit)
                        runBlocking { release.await() }
                    }
                }
            holding.await()
            val waiters =
                List(5) {
                    launch(start = CoroutineStart.UNDISPATCHED) { pool.use(worlds[1]) { tileOf(it, 0, 0) } }
                }

            waiters.forEach { it.cancelAndJoin() }
            release.complete(Unit)
            holder.join()

            withTimeout(TIMEOUT_MILLIS) {
                assertThat(pool.use(worlds[2]) { tileOf(it, 0, 0) }).hasLength(TILE_CELLS * TILE_CELLS)
            }
        }

    @Test
    fun aCallerCancelledWhileItsBlockRunsStillFreesTheGenerator() =
        runBlocking {
            val pool = pool(1)
            val inside = CompletableDeferred<Unit>()
            val job =
                launch {
                    pool.use(worlds[0]) {
                        inside.complete(Unit)
                        Thread.sleep(SLOW_BLOCK_MILLIS)
                    }
                }
            inside.await()

            job.cancelAndJoin()

            withTimeout(TIMEOUT_MILLIS) { assertThat(pool.use(worlds[0]) { tileOf(it, 0, 0) }).isNotEmpty() }
        }

    @Test
    fun anErrorInTheBlockStillFreesTheGenerator() =
        runBlocking {
            val pool = pool(1)

            repeat(3) {
                val thrown = runCatching { pool.use(worlds[0]) { error("boom") } }.exceptionOrNull()
                assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
            }

            withTimeout(TIMEOUT_MILLIS) { assertThat(pool.use(worlds[0]) { tileOf(it, 0, 0) }).isNotEmpty() }
        }

    @Test
    fun shuttingDownWaitsForRunningWorkThenRefusesNewWork() =
        runBlocking {
            val pool = pool(2)
            val inside = CompletableDeferred<Unit>()
            val finished = AtomicInteger()
            val running =
                launch {
                    pool.use(worlds[0]) {
                        inside.complete(Unit)
                        Thread.sleep(SLOW_BLOCK_MILLIS)
                        finished.incrementAndGet()
                    }
                }
            inside.await()

            pool.shutDown()

            assertThat(finished.get()).isEqualTo(1)
            running.join()
            val thrown = runCatching { pool.use(worlds[0]) { tileOf(it, 0, 0) } }.exceptionOrNull()
            assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        }

    @Test
    fun defaultSizeLeavesACoreFreeButNeverDropsBelowOne() {
        assertThat(GeneratorPool.defaultSize()).isAtLeast(1)
        assertThat(GeneratorPool.defaultSize()).isAtMost(maxOf(1, Runtime.getRuntime().availableProcessors() - 1))
    }

    @Test
    fun theEngineAnswersManyMixedQueriesAtOnce() =
        runBlocking {
            val engine = JniSeedMapEngine(Dispatchers.Default)
            try {
                val world = MapWorld(262, McVersion.V26_3)
                val results =
                    List(24) { index ->
                        async {
                            when (index % 4) {
                                0 -> {
                                    val scale = TileScale.entries[index % TileScale.entries.size]
                                    engine.biomeTile(TileKey(world, scale, index, -index)).biomes.size
                                }

                                1 -> {
                                    val area = BlockArea(index * 512, 0, index * 512 + 2048, 2048)
                                    engine.structuresIn(world, StructureType.VILLAGE, area).size
                                }

                                2 -> {
                                    engine.strongholds(world, 8).size
                                }

                                else -> {
                                    engine.spawn(world).x
                                }
                            }
                        }
                    }.awaitAll()

                fun answersOf(kind: Int) = results.filterIndexed { i, _ -> i % 4 == kind }.toSet()
                assertThat(answersOf(0)).containsExactly(TILE_CELLS * TILE_CELLS)
                assertThat(answersOf(2)).containsExactly(8)
                assertThat(answersOf(3)).containsExactly(-544)
            } finally {
                engine.shutDown()
            }
            Unit
        }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val SLOW_BLOCK_MILLIS = 300L
    }
}
