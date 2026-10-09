package dev.cniekirk.wikidroid.core.seedmap

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WikiTitlesTest {
    @Test
    fun `every structure has a title`() {
        for (type in StructureType.entries) {
            assertThat(StructureWikiTitles.titleFor(type)).isNotEmpty()
        }
    }

    @Test
    fun `structure titles follow the names the wiki uses today`() {
        assertThat(StructureWikiTitles.titleFor(StructureType.SWAMP_HUT)).isEqualTo("Swamp Hut")
        assertThat(StructureWikiTitles.titleFor(StructureType.JUNGLE_TEMPLE)).isEqualTo("Jungle Pyramid")
        assertThat(StructureWikiTitles.titleFor(StructureType.BURIED_TREASURE)).isEqualTo("Buried Treasure")
        assertThat(StructureWikiTitles.titleFor(StructureType.MONUMENT)).isEqualTo("Ocean Monument")
        assertThat(StructureWikiTitles.titleFor(StructureType.MANSION)).isEqualTo("Woodland Mansion")
        assertThat(StructureWikiTitles.titleFor(StructureType.FORTRESS)).isEqualTo("Nether Fortress")
        assertThat(StructureWikiTitles.titleFor(StructureType.BASTION)).isEqualTo("Bastion Remnant")
    }

    @Test
    fun `both ruined portals open the same article`() {
        assertThat(StructureWikiTitles.titleFor(StructureType.RUINED_PORTAL))
            .isEqualTo(StructureWikiTitles.titleFor(StructureType.RUINED_PORTAL_NETHER))
    }

    @Test
    fun `every biome cubiomes can return has a title`() {
        for (id in BiomeCatalog.names.keys) {
            assertThat(BiomeWikiTitles.titleFor(id)).isNotEmpty()
        }
    }

    @Test
    fun `biome titles are the catalog name in title case`() {
        assertThat(BiomeWikiTitles.titleFor(4)).isEqualTo("Forest")
        assertThat(BiomeWikiTitles.titleFor(29)).isEqualTo("Dark Forest")
        assertThat(BiomeWikiTitles.titleFor(14)).isEqualTo("Mushroom Fields")
        assertThat(BiomeWikiTitles.titleFor(185)).isEqualTo("Cherry Grove")
        assertThat(BiomeWikiTitles.titleFor(9)).isEqualTo("The End")
    }

    @Test
    fun `old biome ids open the article of the biome they became`() {
        // Ids are stable across versions: the id that was `mountains` is `windswept_hills` today.
        assertThat(BiomeCatalog.names[3]).isEqualTo("windswept_hills")
        assertThat(BiomeWikiTitles.titleFor(3)).isEqualTo("Windswept Hills")
        assertThat(BiomeWikiTitles.titleFor(12)).isEqualTo("Snowy Plains")
        assertThat(BiomeWikiTitles.titleFor(23)).isEqualTo("Sparse Jungle")
    }

    @Test
    fun `a variant without a page of its own opens its parent`() {
        assertThat(BiomeWikiTitles.titleFor(162)).isEqualTo("Windswept Gravelly Hills")
    }

    @Test
    fun `an id cubiomes does not know has no title`() {
        assertThat(BiomeWikiTitles.titleFor(-1)).isNull()
        assertThat(BiomeWikiTitles.titleFor(100)).isNull()
        assertThat(BiomeWikiTitles.titleFor(256)).isNull()
    }

    @Test
    fun `the catalog only holds ids a palette can colour`() {
        assertThat(BiomeCatalog.names.keys.all { it in 0..255 }).isTrue()
        assertThat(BiomeCatalog.names.values.all { it.isNotBlank() && it == it.lowercase() }).isTrue()
    }
}
