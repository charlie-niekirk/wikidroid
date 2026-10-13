package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.BiomeLegendEntry
import dev.cniekirk.wikidroid.core.seedmap.BiomeWikiTitles
import dev.cniekirk.wikidroid.core.seedmap.BlockArea
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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

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
    /** The biomes the selected version and dimension generate, by name, for the legend sheet. */
    val biomeLegend: ImmutableList<BiomeLegendEntry> = persistentListOf(),
    val selection: MapSelection? = null,
    /** Chunk and region lines over the map. */
    val showGrid: Boolean = false,
    /** The plus at the centre of the map, which the coordinate readout names. */
    val showCrosshair: Boolean = true,
    /** The structure the search is finding the nearest of, while it looks. */
    val findingNearest: StructureType? = null,
    /** A structure the search found none of within reach, until the snackbar saying so has been shown. */
    val nearestMissing: StructureType? = null,
) {
    val world: MapWorld get() = MapWorld(seed, version, dimension)

    /** This seed in this version, if it is in the saved list. */
    val savedEntry: SavedSeed? get() = savedSeeds.firstOrNull { it.seed == seed && it.version == version.label }

    /** Whether this seed in this version is already in the saved list. */
    val isSaved: Boolean get() = savedEntry != null

    /** The world to find the spawn of, or null when the dimension has none. */
    internal val spawnQuery: MapWorld? get() = world.takeIf { it.dimension == Dimension.OVERWORLD }

    /** The world to find strongholds in, or null when they are toggled off or do not exist there. */
    internal val strongholdQuery: MapWorld?
        get() =
            world.takeIf { StructureType.STRONGHOLD in enabledStructures && StructureType.STRONGHOLD.isAvailableIn(it) }

    /** Structures the current toggles and view ask for; equal queries are not searched twice. */
    internal val pinQuery: PinQuery
        get() {
            val world = world
            val types = enabledStructures.filter { it.isRegionBased && it.isAvailableIn(world) }.toSet()
            val area = viewport?.let(::pinSearchArea)
            return PinQuery(world, types, area, hidden = viewport != null && area == null)
        }

    /** The same state in another world; everything that belongs to the old world is dropped. */
    fun inWorld(
        seed: Long = this.seed,
        version: McVersion = this.version,
        dimension: Dimension = this.dimension,
    ): SeedMapState =
        if (seed == this.seed && version == this.version && dimension == this.dimension) {
            this
        } else {
            copy(
                seed = seed,
                version = version,
                dimension = dimension,
                spawn = null,
                pins = persistentListOf(),
                strongholds = persistentListOf(),
                pinsHidden = false,
                selection = null,
            )
        }

    /**
     * [version] (and [seed]). The Nether and End are missing from older versions; fall back to the Overworld
     * rather than hold an impossible world.
     */
    fun withVersion(
        version: McVersion,
        seed: Long = this.seed,
    ): SeedMapState =
        inWorld(
            seed = seed,
            version = version,
            dimension = dimension.takeIf { version.supports(it) } ?: Dimension.OVERWORLD,
        )

    /** [dimension], if this version has it. */
    fun withDimension(dimension: Dimension): SeedMapState =
        if (version.supports(dimension)) inWorld(dimension = dimension) else this

    fun toggled(type: StructureType): SeedMapState =
        if (type in enabledStructures) {
            copy(
                enabledStructures = (enabledStructures - type).toImmutableSet(),
                pins = pins.filterNot { it.type == type }.toImmutableList(),
                strongholds = if (type == StructureType.STRONGHOLD) persistentListOf() else strongholds,
                selection = selection?.takeUnless { it is MapSelection.Structure && it.structure.type == type },
            )
        } else {
            copy(enabledStructures = (enabledStructures + type).toImmutableSet())
        }

    /** The spawn found for [world] (null: there is none), unless the map has moved to another world since. */
    fun withSpawn(
        world: MapWorld?,
        spawn: BlockPos?,
    ): SeedMapState = if (world == null || world == this.world) copy(spawn = spawn) else this

    /**
     * The strongholds found for [world] (null: they are off or don't exist there), unless the world changed
     * or strongholds were switched off while they were found.
     */
    fun withStrongholds(
        world: MapWorld?,
        found: List<BlockPos>,
    ): SeedMapState =
        when {
            world == null -> {
                copy(strongholds = persistentListOf())
            }

            world == this.world && StructureType.STRONGHOLD in enabledStructures -> {
                copy(
                    strongholds = found.toImmutableList(),
                )
            }

            else -> {
                this
            }
        }

    /** The legend for [version] and [dimension], unless another one was picked while it loaded. */
    fun withLegend(
        version: McVersion,
        dimension: Dimension,
        legend: List<BiomeLegendEntry>,
    ): SeedMapState =
        if (version == this.version &&
            dimension == this.dimension
        ) {
            copy(biomeLegend = legend.toImmutableList())
        } else {
            this
        }

    /** The pins [query] found, unless the world changed. A type switched off while it ran does not come back. */
    internal fun withPins(
        query: PinQuery,
        found: List<StructurePos>,
    ): SeedMapState =
        when {
            query.world != world -> this
            query.searchArea == null -> copy(pins = persistentListOf(), pinsHidden = query.hidden)
            else -> copy(pins = found.filter { it.type in enabledStructures }.toImmutableList(), pinsHidden = false)
        }

    /**
     * The nearest [type] the search found in [world] (null: none within reach). Found, it is turned on and
     * selected, so its pin shows and its sheet opens; missing, the screen says so. A search for a world the map
     * has since left only stops looking.
     */
    fun withNearest(
        world: MapWorld,
        type: StructureType,
        found: StructurePos?,
    ): SeedMapState =
        when {
            world != this.world -> {
                copy(findingNearest = null)
            }

            found == null -> {
                copy(findingNearest = null, nearestMissing = type)
            }

            else -> {
                copy(
                    enabledStructures = (enabledStructures + type).toImmutableSet(),
                    selection = MapSelection.Structure(found),
                    findingNearest = null,
                )
            }
        }

    /** Selects the biome tapped at [pos], unless the world changed while it was looked up. */
    fun withBiomeSelection(
        world: MapWorld,
        pos: BlockPos,
        biome: Int,
    ): SeedMapState = if (world == this.world) copy(selection = MapSelection.Biome(pos, biome)) else this

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

    /** A row of the legend sheet: opens that biome or structure's article. */
    data class OpenLegendArticle(
        val title: String,
    ) : SeedMapAction

    data object GoToSpawn : SeedMapAction

    data class GoToCoordinates(
        val x: Int,
        val z: Int,
    ) : SeedMapAction

    /** A "Nearest <structure>" row of the seed search: centre the map on the closest one and select it. */
    data class GoToNearestStructure(
        val type: StructureType,
    ) : SeedMapAction

    /** The snackbar saying [SeedMapState.nearestMissing] was not found has been shown. */
    data object NearestMissingShown : SeedMapAction

    /** The Grid switch in the layers sheet. */
    data class SetGrid(
        val show: Boolean,
    ) : SeedMapAction

    /** The Crosshair switch in the layers sheet. */
    data class SetCrosshair(
        val show: Boolean,
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

/** Structures the current toggles and view ask for; equal queries are not searched twice. */
internal data class PinQuery(
    val world: MapWorld,
    val types: Set<StructureType>,
    val area: BlockArea?,
    /** The view is too wide to search. */
    val hidden: Boolean,
) {
    /** Where to search, or null when there is nothing to search: the view is too wide, unknown or nothing is on. */
    val searchArea: BlockArea? get() = area.takeUnless { hidden || types.isEmpty() }
}
