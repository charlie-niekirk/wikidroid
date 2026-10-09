package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.BiomeWikiTitles
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.StructurePos
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.seedmap.StructureWikiTitles
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** What a tap on the map picked, for the bottom sheet. */
sealed interface MapSelection {
    val pos: BlockPos

    /** The minecraft.wiki article the sheet's "Open wiki article" opens, if there is one. */
    val wikiTitle: String?

    data class Biome(
        override val pos: BlockPos,
        val biomeId: Int,
    ) : MapSelection {
        override val wikiTitle: String? get() = BiomeWikiTitles.titleFor(biomeId)
    }

    data class Structure(
        val structure: StructurePos,
    ) : MapSelection {
        override val pos: BlockPos get() = structure.pos
        override val wikiTitle: String get() = StructureWikiTitles.titleFor(structure.type)
    }

    data class Spawn(
        override val pos: BlockPos,
    ) : MapSelection {
        override val wikiTitle: String get() = SPAWN_WIKI_TITLE
    }
}

private const val SPAWN_WIKI_TITLE = "World spawn"

/**
 * The seed map screen. Tiles are not part of the state: the canvas pulls them from the tile cache as it
 * draws. [pins] holds the region-based structures around [viewport] and [strongholds] the strongholds,
 * both only for toggled-on types; [pinsHidden] is true while the view is too wide to search.
 */
@Immutable
data class SeedMapState(
    val seed: Long = DEFAULT_SEED,
    val version: McVersion = McVersion.newest,
    val dimension: Dimension = Dimension.OVERWORLD,
    val enabledStructures: ImmutableSet<StructureType> = DEFAULT_STRUCTURES,
    val savedSeeds: ImmutableList<SavedSeed> = persistentListOf(),
    /** The last view the canvas reported; null until it has a size. */
    val viewport: MapViewport? = null,
    val spawn: BlockPos? = null,
    val pins: ImmutableList<StructurePos> = persistentListOf(),
    val strongholds: ImmutableList<BlockPos> = persistentListOf(),
    val pinsHidden: Boolean = false,
    val selection: MapSelection? = null,
) {
    val world: MapWorld get() = MapWorld(seed, version, dimension)

    /** Whether this seed in this version is already in the saved list. */
    val isSaved: Boolean get() = savedSeeds.any { it.seed == seed && it.version == version.label }

    companion object {
        const val DEFAULT_SEED = 262L
        val DEFAULT_STRUCTURES: ImmutableSet<StructureType> =
            persistentSetOf(StructureType.VILLAGE, StructureType.STRONGHOLD)
    }
}

sealed interface SeedMapAction {
    /** The seed field was submitted: text is parsed like the game does (a number, else hashed, blank is random). */
    data class SubmitSeed(
        val text: String,
    ) : SeedMapAction

    data class SelectVersion(
        val version: McVersion,
    ) : SeedMapAction

    data class SelectDimension(
        val dimension: Dimension,
    ) : SeedMapAction

    data class ToggleStructure(
        val type: StructureType,
    ) : SeedMapAction

    /** Saves the current seed and version, with an optional [label]. */
    data class SaveSeed(
        val label: String? = null,
    ) : SeedMapAction

    data class DeleteSavedSeed(
        val saved: SavedSeed,
    ) : SeedMapAction

    /** Switches to a saved seed and its version. Ignored if this build no longer knows the version. */
    data class LoadSavedSeed(
        val saved: SavedSeed,
    ) : SeedMapAction

    /** The canvas moved or resized. */
    data class ViewportChanged(
        val viewport: MapViewport,
    ) : SeedMapAction

    /** A tap on the bare map: look up the biome there. */
    data class SelectPoint(
        val pos: BlockPos,
    ) : SeedMapAction

    /** A tap on a structure or stronghold pin. */
    data class SelectStructure(
        val structure: StructurePos,
    ) : SeedMapAction

    data object SelectSpawn : SeedMapAction

    data object DismissSelection : SeedMapAction

    /** "Open wiki article" in the selection sheet. */
    data object OpenWikiArticle : SeedMapAction

    data object GoToSpawn : SeedMapAction

    data class GoToCoordinates(
        val x: Int,
        val z: Int,
    ) : SeedMapAction
}

sealed interface SeedMapEffect {
    data class OpenArticle(
        val title: String,
    ) : SeedMapEffect

    /** Move the canvas so [pos] is at its centre. */
    data class CenterOn(
        val pos: BlockPos,
    ) : SeedMapEffect
}
