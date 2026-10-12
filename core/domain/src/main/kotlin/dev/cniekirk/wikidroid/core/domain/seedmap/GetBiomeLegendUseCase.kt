package dev.cniekirk.wikidroid.core.domain.seedmap

import dev.cniekirk.wikidroid.core.seedmap.BiomeLegendEntry
import dev.cniekirk.wikidroid.core.seedmap.BiomePalette
import dev.cniekirk.wikidroid.core.seedmap.BiomeWikiTitles
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.zacsweers.metro.Inject

/**
 * The legend's biome list for [McVersion] and [Dimension]: every biome they can generate that has a wiki
 * article, with its map colour, by title. Empty if the engine failed.
 */
@Inject
class GetBiomeLegendUseCase(
    private val engine: SeedMapEngine,
) {
    suspend operator fun invoke(
        version: McVersion,
        dimension: Dimension,
    ): List<BiomeLegendEntry> {
        val ids = attempt { engine.biomesIn(version, dimension) }.orEmpty()
        val colors = attempt { engine.biomeColors() }
        return ids
            .mapNotNull { id ->
                BiomeWikiTitles.titleFor(id)?.let { title ->
                    BiomeLegendEntry(id, title, colors?.getOrNull(id) ?: BiomePalette.UNKNOWN)
                }
            }.sortedBy { it.title }
    }
}
