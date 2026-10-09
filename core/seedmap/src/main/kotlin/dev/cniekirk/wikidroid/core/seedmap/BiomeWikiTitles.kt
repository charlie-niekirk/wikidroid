package dev.cniekirk.wikidroid.core.seedmap

/**
 * The minecraft.wiki article for a biome. A biome's title is its [BiomeCatalog] name in title case
 * ("dark_forest" is "Dark Forest"), except where the wiki names the page differently.
 */
object BiomeWikiTitles {
    private val overrides: Map<Int, String> =
        mapOf(
            // Variant biomes without a page of their own are described on the page of the biome they vary.
            162 to "Windswept Gravelly Hills", // modified_gravelly_mountains
        )

    /** The article title for [biomeId], or null if cubiomes has no such biome. */
    fun titleFor(biomeId: Int): String? {
        overrides[biomeId]?.let { return it }
        val name = BiomeCatalog.names[biomeId] ?: return null
        return name.split('_').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
}
