package dev.cniekirk.wikidroid.core.seedmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real `libseedmap.so` and compares it with values from the pinned cubiomes commit.
 *
 * Where the expected values come from: a throwaway C program (kept out of the repo) built on the host
 * from the same submodule commit (`4f04235`, see docs/PROGRESS.md) with `clang -O1`. It called
 * `setupGenerator`, `applySeed`, `getSpawn` and `getBiomeAt(scale = 1, y = 63)` exactly as the JNI layer
 * does and printed the cases below. They therefore catch JNI, build-flag and ABI mistakes (a wrong
 * `-fwrapv`, a 32-bit truncation) but not a bug in cubiomes itself.
 */
@RunWith(AndroidJUnit4::class)
class SeedMapEngineGoldenTest {
    private val engine = JniSeedMapEngine(Dispatchers.Default)

    @Test
    fun spawnMatchesTheReferenceBuild() =
        runBlocking {
            for (case in CASES) {
                assertThat(engine.spawn(case.world)).isEqualTo(case.spawn)
            }
        }

    @Test
    fun biomesMatchTheReferenceBuild() =
        runBlocking {
            for (case in CASES) {
                for (sample in case.biomes) {
                    assertThat(engine.biomeAt(case.world, sample.x, sample.z))
                        .isEqualTo(sample.biome)
                }
            }
        }

    /** Queries interleave versions and seeds, which forces the engine to re-seed and re-create its generator. */
    @Test
    fun switchingWorldsBackAndForthGivesTheSameAnswers() =
        runBlocking {
            val first = CASES.first()
            val last = CASES.last()
            repeat(2) {
                assertThat(engine.spawn(first.world)).isEqualTo(first.spawn)
                assertThat(engine.spawn(last.world)).isEqualTo(last.spawn)
            }
        }

    @Test
    fun anotherDimensionOfTheSameVersionLeavesTheOverworldIntact() =
        runBlocking {
            val case = CASES.first { it.version == McVersion.V26_3 }
            val nether = case.world.copy(dimension = Dimension.NETHER)
            val end = case.world.copy(dimension = Dimension.END)

            assertThat(engine.biomeAt(nether, 0, 0, y = 64)).isNotNull()
            assertThat(engine.biomeAt(end, 0, 0, y = 64)).isNotNull()
            assertThat(engine.spawn(case.world)).isEqualTo(case.spawn)
        }

    @Test
    fun rejectsInputsBeforeTheyReachNativeCode() =
        runBlocking {
            val world = CASES.first().world
            assertThrows<IllegalArgumentException> { engine.biomeAt(world, SeedMapEngine.MAX_COORDINATE + 1, 0) }
            assertThrows<IllegalArgumentException> { engine.biomeAt(world, 0, 0, y = SeedMapEngine.MAX_Y + 1) }
            assertThrows<IllegalArgumentException> { engine.spawn(world.copy(dimension = Dimension.END)) }
        }

    private suspend inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        val thrown = runCatching { block() }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(T::class.java)
    }

    private class BiomeSample(
        val x: Int,
        val z: Int,
        val biome: Int,
    )

    private class GoldenCase(
        val seed: Long,
        val version: McVersion,
        val spawn: BlockPos,
        val biomes: List<BiomeSample>,
    ) {
        val world = MapWorld(seed, version)
    }

    private companion object {
        val CASES =
            listOf(
                GoldenCase(
                    seed = 262L,
                    version = McVersion.V1_12,
                    spawn = BlockPos(-12, -68),
                    biomes =
                        listOf(
                            BiomeSample(0, 0, 6), // swamp
                            BiomeSample(100, -250, 1), // plains
                            BiomeSample(-1234, 987, 35), // savanna
                            BiomeSample(4000, 4000, 24), // deep_ocean
                            BiomeSample(-6000, 2500, 29), // dark_forest
                        ),
                ),
                GoldenCase(
                    seed = 262L,
                    version = McVersion.V1_18,
                    spawn = BlockPos(420, -92),
                    biomes =
                        listOf(
                            BiomeSample(0, 0, 14), // mushroom_fields
                            BiomeSample(100, -250, 49), // deep_cold_ocean
                            BiomeSample(-1234, 987, 1), // plains
                            BiomeSample(4000, 4000, 16), // beach
                            BiomeSample(-6000, 2500, 4), // forest
                        ),
                ),
                GoldenCase(
                    seed = 262L,
                    version = McVersion.V26_3,
                    spawn = BlockPos(-544, -432),
                    biomes =
                        listOf(
                            BiomeSample(0, 0, 14), // mushroom_fields
                            BiomeSample(100, -250, 49), // deep_cold_ocean
                            BiomeSample(-1234, 987, 1), // plains
                            BiomeSample(4000, 4000, 16), // beach
                            BiomeSample(-6000, 2500, 4), // forest
                        ),
                ),
                GoldenCase(
                    seed = -4172144997902289642L,
                    version = McVersion.V1_12,
                    spawn = BlockPos(8, -88),
                    biomes =
                        listOf(
                            BiomeSample(0, 0, 12), // snowy_tundra
                            BiomeSample(100, -250, 18), // wooded_hills
                            BiomeSample(-1234, 987, 12), // snowy_tundra
                            BiomeSample(4000, 4000, 24), // deep_ocean
                            BiomeSample(-6000, 2500, 7), // river
                        ),
                ),
                GoldenCase(
                    seed = -4172144997902289642L,
                    version = McVersion.V1_18,
                    spawn = BlockPos(-48, -96),
                    biomes =
                        listOf(
                            BiomeSample(0, 0, 45), // lukewarm_ocean
                            BiomeSample(100, -250, 48), // deep_lukewarm_ocean
                            BiomeSample(-1234, 987, 44), // warm_ocean
                            BiomeSample(4000, 4000, 7), // river
                            BiomeSample(-6000, 2500, 46), // cold_ocean
                        ),
                ),
                GoldenCase(
                    seed = -4172144997902289642L,
                    version = McVersion.V26_3,
                    spawn = BlockPos(-448, -432),
                    biomes =
                        listOf(
                            BiomeSample(0, 0, 45), // lukewarm_ocean
                            BiomeSample(100, -250, 48), // deep_lukewarm_ocean
                            BiomeSample(-1234, 987, 44), // warm_ocean
                            BiomeSample(4000, 4000, 7), // river
                            BiomeSample(-6000, 2500, 46), // cold_ocean
                        ),
                ),
                GoldenCase(
                    seed = 1L,
                    version = McVersion.V1_12,
                    spawn = BlockPos(164, 256),
                    biomes =
                        listOf(
                            BiomeSample(0, 0, 0), // ocean
                            BiomeSample(100, -250, 4), // forest
                            BiomeSample(-1234, 987, 5), // taiga
                            BiomeSample(4000, 4000, 4), // forest
                            BiomeSample(-6000, 2500, 0), // ocean
                        ),
                ),
                GoldenCase(
                    seed = 1L,
                    version = McVersion.V1_18,
                    spawn = BlockPos(112, 176),
                    biomes =
                        listOf(
                            BiomeSample(0, 0, 24), // deep_ocean
                            BiomeSample(100, -250, 24), // deep_ocean
                            BiomeSample(-1234, 987, 45), // lukewarm_ocean
                            BiomeSample(4000, 4000, 4), // forest
                            BiomeSample(-6000, 2500, 0), // ocean
                        ),
                ),
                GoldenCase(
                    seed = 1L,
                    version = McVersion.V26_3,
                    spawn = BlockPos(160, 160),
                    biomes =
                        listOf(
                            BiomeSample(0, 0, 24), // deep_ocean
                            BiomeSample(100, -250, 24), // deep_ocean
                            BiomeSample(-1234, 987, 45), // lukewarm_ocean
                            BiomeSample(4000, 4000, 4), // forest
                            BiomeSample(-6000, 2500, 0), // ocean
                        ),
                ),
            )
    }
}
