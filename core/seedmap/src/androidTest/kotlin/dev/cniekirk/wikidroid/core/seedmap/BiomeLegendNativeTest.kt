package dev.cniekirk.wikidroid.core.seedmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** [SeedMapEngine.biomesIn], which feeds the legend, against the pinned cubiomes. */
@RunWith(AndroidJUnit4::class)
class BiomeLegendNativeTest {
    private val engine = JniSeedMapEngine(Dispatchers.Default)

    @After
    fun tearDown() = runBlocking { engine.shutDown() }

    private fun names(
        version: McVersion,
        dimension: Dimension,
    ): Set<String> =
        runBlocking { engine.biomesIn(version, dimension) }
            .map { checkNotNull(NativeSeedMap.nativeBiomeName(version.nativeId, it)) }
            .toSet()

    @Test
    fun theNewestNetherHasItsFiveBiomes() {
        assertThat(names(McVersion.newest, Dimension.NETHER)).containsExactly(
            "nether_wastes",
            "soul_sand_valley",
            "crimson_forest",
            "warped_forest",
            "basalt_deltas",
        )
    }

    @Test
    fun theNewestEndHasItsFiveBiomes() {
        assertThat(names(McVersion.newest, Dimension.END)).containsExactly(
            "the_end",
            "small_end_islands",
            "end_midlands",
            "end_highlands",
            "end_barrens",
        )
    }

    @Test
    fun theOverworldHasNeitherOtherDimensionsBiomesNorTheVoid() {
        val overworld = names(McVersion.newest, Dimension.OVERWORLD)

        assertThat(overworld).containsAtLeast("plains", "ocean", "desert", "deep_dark", "pale_garden")
        assertThat(overworld).containsNoneOf("nether_wastes", "the_end", "end_highlands", "the_void", "basalt_deltas")
    }

    @Test
    fun anOlderVersionHasOnlyTheBiomesItHad() {
        val old = names(McVersion.V1_12, Dimension.OVERWORLD)
        val new = names(McVersion.V1_20, Dimension.OVERWORLD)

        assertThat(old).contains("plains")
        assertThat(old).containsNoneOf("cherry_grove", "deep_dark", "lush_caves", "meadow")
        assertThat(new).contains("cherry_grove")
        // 1.12 has its hill and mutated variants, which later versions folded into fewer biomes.
        assertThat(old).containsAtLeast("snowy_tundra", "desert_hills")
        assertThat(new).doesNotContain("snowy_tundra")
    }

    @Test
    fun dimensionsAVersionLacksHaveNoBiomes() =
        runBlocking {
            assertThat(engine.biomesIn(McVersion.V1_12, Dimension.NETHER)).isEmpty()
            assertThat(engine.biomesIn(McVersion.V1_8, Dimension.END)).isEmpty()
            assertThat(engine.biomesIn(McVersion.B1_7, Dimension.END)).isEmpty()
        }

    @Test
    fun theIdsAreAscendingAndEveryOneHasAWikiTitle() =
        runBlocking {
            for (version in McVersion.entries) {
                for (dimension in Dimension.entries) {
                    val ids = engine.biomesIn(version, dimension)
                    assertThat(ids).isInOrder()
                    ids.forEach { assertThat(BiomeWikiTitles.titleFor(it)).isNotNull() }
                }
            }
        }

    @Test
    fun everyBiomeTheLegendListsCanBePaintedInThatWorld() =
        runBlocking {
            // Whatever biome the generator paints must be in the legend, or the map would show a colour
            // the legend cannot name: sample a wide area of the world for the newest version.
            val world = MapWorld(262, McVersion.newest)
            val listed = engine.biomesIn(world.version, world.dimension).toSet()
            val seen = mutableSetOf<Int>()
            for (tileX in -2..1) {
                for (tileZ in -2..1) {
                    seen += engine.biomeTile(TileKey(world, TileScale.CELL_256, tileX, tileZ)).biomes.toSet()
                }
            }

            assertThat(listed.containsAll(seen)).isTrue()
        }
}
