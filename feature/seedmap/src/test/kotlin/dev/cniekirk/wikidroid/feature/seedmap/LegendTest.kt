package dev.cniekirk.wikidroid.feature.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.BiomeLegendEntry
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class LegendTest {
    private val overworld =
        legendStructures(
            StructureType.availableIn(MapWorld(1, McVersion.newest, Dimension.OVERWORLD)),
            Dimension.OVERWORLD,
            spawnTitle = "World spawn",
        )

    @Test
    fun theOverworldLegendStartsWithTheSpawn() {
        assertThat(overworld.first().title).isEqualTo("World spawn")
        assertThat(overworld.first().type).isNull()
        assertThat(overworld.first().icon).isEqualTo(SpawnIcon)
    }

    @Test
    fun otherDimensionsHaveNoSpawnRow() {
        val nether =
            legendStructures(
                StructureType.availableIn(MapWorld(1, McVersion.newest, Dimension.NETHER)),
                Dimension.NETHER,
                spawnTitle = "World spawn",
            )

        assertThat(nether.map { it.title }).doesNotContain("World spawn")
        assertThat(nether.map { it.type }).containsExactly(
            StructureType.RUINED_PORTAL_NETHER,
            StructureType.FORTRESS,
            StructureType.BASTION,
            StructureType.NETHER_FOSSIL,
        )
    }

    @Test
    fun everyStructureRowCarriesTheIconOfItsType() {
        overworld.filter { it.type != null }.forEach { assertThat(it.icon).isEqualTo(it.type!!.icon()) }
    }

    @Test
    fun aBlankQueryKeepsEverything() {
        assertThat(overworld.matching("")).isEqualTo(overworld)
        assertThat(overworld.matching("   ")).isEqualTo(overworld)
    }

    @Test
    fun aQueryMatchesPartOfANameWhateverItsCase() {
        assertThat(overworld.matching("  PYRA ").map { it.title })
            .containsExactly("Desert Pyramid", "Jungle Pyramid")
    }

    @Test
    fun aQueryFiltersBiomesToo() {
        val biomes =
            persistentListOf(
                BiomeLegendEntry(1, "Plains", 0),
                BiomeLegendEntry(4, "Forest", 0),
                BiomeLegendEntry(27, "Birch Forest", 0),
            )

        assertThat(biomes.matching("forest").map { it.id }).containsExactly(4, 27).inOrder()
        assertThat(biomes.matching("nope")).isEmpty()
    }
}
