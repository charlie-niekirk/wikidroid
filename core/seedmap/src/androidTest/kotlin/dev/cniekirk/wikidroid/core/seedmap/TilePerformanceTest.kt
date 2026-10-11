package dev.cniekirk.wikidroid.core.seedmap

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A measurement, not a check: how long tiles, structure searches and strongholds take on this device, and
 * how a screenful of tiles scales with the pool size. It only runs when asked for
 * (`-Pandroid.testInstrumentationRunnerArguments.perf=true`) and writes its numbers to logcat under the tag
 * `SeedMapPerf`. Session 15 recorded the results in `docs/PROGRESS.md`.
 */
@RunWith(AndroidJUnit4::class)
class TilePerformanceTest {
    private fun enabled() = InstrumentationRegistry.getArguments().getString("perf") == "true"

    private fun log(line: String) {
        Log.i(TAG, line)
    }

    private fun nowMillis(): Double = System.nanoTime() / NANOS_PER_MILLI

    private inline fun median(
        repeats: Int,
        block: (Int) -> Unit,
    ): Double {
        val times =
            DoubleArray(repeats) { i ->
                val start = nowMillis()
                block(i)
                nowMillis() - start
            }
        times.sort()
        return times[repeats / 2]
    }

    @Test
    fun singleTileLatency() =
        runBlocking {
            assumeTrue(enabled())
            val engine = JniSeedMapEngine(Dispatchers.Default)
            try {
                log("device cpus=${Runtime.getRuntime().availableProcessors()}")
                for (version in listOf(
                    McVersion.V1_12,
                    McVersion.V1_17,
                    McVersion.V1_18,
                    McVersion.V1_21_1,
                    McVersion.V26_3,
                )) {
                    for (scale in TileScale.entries) {
                        val world = MapWorld(262, version)
                        engine.biomeTile(TileKey(world, scale, 0, 0)) // sets the generator up
                        val ms = median(5) { i -> runBlocking { engine.biomeTile(TileKey(world, scale, i + 1, i)) } }
                        log("tile ${version.label} ${scale.blocksPerCell}b/cell median=${"%.1f".format(ms)}ms")
                    }
                }
                for (dimension in listOf(Dimension.NETHER, Dimension.END)) {
                    val world = MapWorld(262, McVersion.newest, dimension)
                    engine.biomeTile(TileKey(world, TileScale.CELL_4, 0, 0))
                    val ms =
                        median(5) { i -> runBlocking { engine.biomeTile(TileKey(world, TileScale.CELL_4, i + 1, i)) } }
                    log("tile ${McVersion.newest.label} $dimension 4b/cell median=${"%.1f".format(ms)}ms")
                }
            } finally {
                engine.shutDown()
            }
        }

    @Test
    fun aScreenfulOfTilesByPoolSize() =
        runBlocking {
            assumeTrue(enabled())
            // About what a 1080x2400 phone asks for at 3 blocks per pixel: 4 x 7 tiles of 1024 blocks.
            val tiles = (-2..1).flatMap { x -> (-4..2).map { z -> x to z } }
            val cpus = Runtime.getRuntime().availableProcessors()
            for (version in listOf(McVersion.V1_12, McVersion.newest)) {
                for (size in listOf(1, 2, 4, (cpus - 1).coerceAtLeast(1), cpus).distinct()) {
                    val pool = GeneratorPool(Dispatchers.Default, size)
                    val world = MapWorld(262, version)
                    try {
                        val start = nowMillis()
                        coroutineScope {
                            tiles
                                .map { (x, z) ->
                                    async {
                                        pool.use(world) { handle ->
                                            NativeSeedMap.nativeGenBiomes(
                                                handle,
                                                TileScale.CELL_4.blocksPerCell,
                                                x * TILE_CELLS,
                                                z * TILE_CELLS,
                                                TILE_CELLS,
                                                TILE_CELLS,
                                                SeedMapEngine.SEA_LEVEL shr 2,
                                            )
                                        }
                                    }
                                }.awaitAll()
                        }
                        log(
                            "screen ${tiles.size} tiles ${version.label} pool=$size total=${"%.0f".format(
                                nowMillis() - start,
                            )}ms",
                        )
                    } finally {
                        pool.shutDown()
                    }
                }
            }
        }

    @Test
    fun structureAndStrongholdSearches() =
        runBlocking {
            assumeTrue(enabled())
            val engine = JniSeedMapEngine(Dispatchers.Default)
            try {
                val world = MapWorld(262, McVersion.newest)
                val area = BlockArea(-4096, -4096, 4096, 4096)
                engine.spawn(world)
                for (type in listOf(
                    StructureType.VILLAGE,
                    StructureType.MONUMENT,
                    StructureType.MANSION,
                    StructureType.ANCIENT_CITY,
                )) {
                    val ms = median(3) { runBlocking { engine.structuresIn(world, type, area) } }
                    log("structures $type 8192x8192 median=${"%.1f".format(ms)}ms")
                }
                for (version in listOf(McVersion.V1_12, McVersion.V1_17, McVersion.newest)) {
                    val w = MapWorld(262, version)
                    val ms = median(3) { runBlocking { engine.strongholds(w, SeedMapEngine.MAX_STRONGHOLDS) } }
                    log("strongholds 128 ${version.label} median=${"%.0f".format(ms)}ms")
                }
            } finally {
                engine.shutDown()
            }
        }

    private companion object {
        const val TAG = "SeedMapPerf"
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
