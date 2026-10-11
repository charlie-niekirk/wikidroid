package dev.cniekirk.wikidroid.core.domain.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.BiomeLegendEntry
import dev.cniekirk.wikidroid.core.seedmap.BiomePalette
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.testing.fake.FakeSeedMapEngine
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetBiomeLegendUseCaseTest {
    private val engine = FakeSeedMapEngine()

    @Test
    fun namesAndColoursEachBiomeSortedByTitle() =
        runTest {
            engine.biomesHandler = { _, _ -> listOf(PLAINS, BIRCH_FOREST, FOREST) }
            engine.colors = IntArray(256) { it }

            val legend = GetBiomeLegendUseCase(engine)(McVersion.newest, Dimension.OVERWORLD)

            assertThat(legend)
                .containsExactly(
                    BiomeLegendEntry(BIRCH_FOREST, "Birch Forest", BIRCH_FOREST),
                    BiomeLegendEntry(FOREST, "Forest", FOREST),
                    BiomeLegendEntry(PLAINS, "Plains", PLAINS),
                ).inOrder()
        }

    @Test
    fun aBiomeWithoutAColourGetsTheUnknownOne() =
        runTest {
            engine.biomesHandler = { _, _ -> listOf(PLAINS) }
            engine.colors = IntArray(0)

            val legend = GetBiomeLegendUseCase(engine)(McVersion.newest, Dimension.OVERWORLD)

            assertThat(legend.single().argb).isEqualTo(BiomePalette.UNKNOWN)
        }

    @Test
    fun anEngineFailureIsAnEmptyLegend() =
        runTest {
            engine.biomesHandler = { _, _ -> error("generator busy") }

            assertThat(GetBiomeLegendUseCase(engine)(McVersion.newest, Dimension.OVERWORLD)).isEmpty()
        }

    private companion object {
        const val PLAINS = 1
        const val FOREST = 4
        const val BIRCH_FOREST = 27
    }
}
