package dev.cniekirk.wikidroid.feature.seedmap

import androidx.lifecycle.ViewModel
import dev.cniekirk.wikidroid.core.data.SeedRepository
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.cniekirk.wikidroid.core.seedmap.SeedParser
import dev.cniekirk.wikidroid.core.seedmap.StructurePos
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.syntax.Syntax
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * Drives the seed map: which world is shown, which structures are toggled on, the pins and selection for
 * the current view, and the saved seeds.
 *
 * The canvas reports its view with [SeedMapAction.ViewportChanged]. Pin searches follow it, but wait for
 * the view to settle (a pan fires many changes) unless the world or the toggles changed, which are
 * answered at once. A change of world drops the pins, spawn and selection immediately, so the old world
 * is never shown over the new one, and any answer that arrives for an earlier world is discarded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One small function per intent, as in the other MVI view models.
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SeedMapViewModel(
    private val engine: SeedMapEngine,
    private val seeds: SeedRepository,
) : ViewModel(),
    OrbitContainerHost<SeedMapState, SeedMapState, SeedMapEffect> {
    // The type is spelled out because the collectors below read `container` while it is being built.
    override val container: OrbitContainer<SeedMapState, SeedMapState, SeedMapEffect> =
        orbitContainer<SeedMapState, SeedMapEffect>(SeedMapState()) {
            coroutineScope {
                launch {
                    seeds.savedSeeds.collect { saved -> reduce { state.copy(savedSeeds = saved.toImmutableList()) } }
                }
                launch {
                    container.stateFlow
                        .map { it.spawnQuery() }
                        .distinctUntilChanged()
                        .collectLatest { world -> loadSpawn(world) }
                }
                launch {
                    container.stateFlow
                        .map { it.strongholdQuery() }
                        .distinctUntilChanged()
                        .collectLatest { world -> loadStrongholds(world) }
                }
                launch {
                    var previous: PinQuery? = null
                    container.stateFlow
                        .map { it.pinQuery() }
                        .distinctUntilChanged()
                        .transformLatest { query ->
                            // Only a moved view is worth waiting on; a new world or toggle is answered at once.
                            if (previous?.world == query.world &&
                                previous?.types == query.types
                            ) {
                                delay(PIN_DEBOUNCE_MILLIS)
                            }
                            previous = query
                            emit(query)
                        }.collectLatest { query -> loadPins(query) }
                }
            }
        }

    fun onAction(action: SeedMapAction) {
        when (action) {
            is SeedMapAction.SubmitSeed -> submitSeed(action.text)
            is SeedMapAction.SelectVersion -> selectVersion(action.version)
            is SeedMapAction.SelectDimension -> selectDimension(action.dimension)
            is SeedMapAction.ToggleStructure -> intent { reduce { state.toggled(action.type) } }
            is SeedMapAction.SaveSeed -> saveSeed(action.label)
            is SeedMapAction.DeleteSavedSeed -> intent { seeds.removeSeed(action.saved) }
            is SeedMapAction.LoadSavedSeed -> loadSavedSeed(action.saved)
            is SeedMapAction.ViewportChanged -> intent { reduce { state.copy(viewport = action.viewport) } }
            is SeedMapAction.SelectPoint -> selectPoint(action.pos)
            is SeedMapAction.SelectStructure -> selectStructure(action.structure)
            SeedMapAction.SelectSpawn -> selectSpawn()
            SeedMapAction.DismissSelection -> intent { reduce { state.copy(selection = null) } }
            SeedMapAction.OpenWikiArticle -> openWikiArticle()
            SeedMapAction.GoToSpawn -> goToSpawn()
            is SeedMapAction.GoToCoordinates -> goToCoordinates(action.x, action.z)
        }
    }

    private fun submitSeed(text: String) = intent { reduce { state.inWorld(seed = SeedParser.parse(text)) } }

    private fun selectStructure(structure: StructurePos) =
        intent { reduce { state.copy(selection = MapSelection.Structure(structure)) } }

    private fun selectSpawn() =
        intent { reduce { state.spawn?.let { state.copy(selection = MapSelection.Spawn(it)) } ?: state } }

    private fun openWikiArticle() =
        intent { state.selection?.wikiTitle?.let { postSideEffect(SeedMapEffect.OpenArticle(it)) } }

    private fun goToCoordinates(
        x: Int,
        z: Int,
    ) = intent {
        val limit = SeedMapEngine.MAX_COORDINATE
        postSideEffect(SeedMapEffect.CenterOn(BlockPos(x.coerceIn(-limit, limit), z.coerceIn(-limit, limit))))
    }

    private fun selectVersion(version: McVersion) =
        intent {
            reduce {
                // The Nether and End are missing from older versions; fall back rather than hold an impossible world.
                val dimension = state.dimension.takeIf { version.supports(it) } ?: Dimension.OVERWORLD
                state.inWorld(version = version, dimension = dimension)
            }
        }

    private fun selectDimension(dimension: Dimension) =
        intent {
            reduce { if (state.version.supports(dimension)) state.inWorld(dimension = dimension) else state }
        }

    private fun saveSeed(label: String?) =
        intent {
            val current = state
            seeds.saveSeed(SavedSeed(current.seed, current.version.label, label?.trim()?.takeIf { it.isNotEmpty() }))
        }

    private fun loadSavedSeed(saved: SavedSeed) =
        intent {
            val version = McVersion.fromLabel(saved.version) ?: return@intent
            reduce {
                state.inWorld(
                    seed = saved.seed,
                    version = version,
                    dimension =
                        state.dimension.takeIf { version.supports(it) } ?: Dimension.OVERWORLD,
                )
            }
        }

    private fun selectPoint(pos: BlockPos) =
        intent {
            val world = state.world
            val biome = attempt { engine.biomeAt(world, pos.x, pos.z) }
            reduce {
                if (biome == null ||
                    state.world != world
                ) {
                    state
                } else {
                    state.copy(selection = MapSelection.Biome(pos, biome))
                }
            }
        }

    private fun goToSpawn() =
        intent {
            val world = state.world
            val spawn =
                state.spawn ?: if (world.dimension == Dimension.OVERWORLD) attempt { engine.spawn(world) } else null
            spawn?.let { postSideEffect(SeedMapEffect.CenterOn(it)) }
        }

    private suspend fun Syntax<SeedMapState, SeedMapEffect>.loadSpawn(world: MapWorld?) {
        if (world == null) {
            reduce { state.copy(spawn = null) }
            return
        }
        val spawn = attempt { engine.spawn(world) }
        reduce { if (state.world == world) state.copy(spawn = spawn) else state }
    }

    private suspend fun Syntax<SeedMapState, SeedMapEffect>.loadStrongholds(world: MapWorld?) {
        if (world == null) {
            reduce { state.copy(strongholds = persistentListOf()) }
            return
        }
        val found = attempt { engine.strongholds(world, SeedMapEngine.MAX_STRONGHOLDS) }.orEmpty()
        reduce {
            if (state.world == world && StructureType.STRONGHOLD in state.enabledStructures) {
                state.copy(strongholds = found.toImmutableList())
            } else {
                state
            }
        }
    }

    private suspend fun Syntax<SeedMapState, SeedMapEffect>.loadPins(query: PinQuery) {
        if (query.hidden || query.area == null || query.types.isEmpty()) {
            reduce {
                if (state.world ==
                    query.world
                ) {
                    state.copy(pins = persistentListOf(), pinsHidden = query.hidden)
                } else {
                    state
                }
            }
            return
        }
        val found: List<StructurePos> =
            coroutineScope {
                query.types
                    .map { type -> async { attempt { engine.structuresIn(query.world, type, query.area) }.orEmpty() } }
                    .awaitAll()
                    .flatten()
            }
        reduce {
            if (state.world == query.world) {
                // A type switched off while this search ran must not come back.
                state.copy(
                    pins = found.filter { it.type in state.enabledStructures }.toImmutableList(),
                    pinsHidden = false,
                )
            } else {
                state
            }
        }
    }

    private companion object {
        const val PIN_DEBOUNCE_MILLIS = 200L
    }
}

/** Structures the current toggles and view ask for; equal queries are not searched twice. */
private data class PinQuery(
    val world: MapWorld,
    val types: Set<StructureType>,
    val area: BlockArea?,
    /** The view is too wide to search. */
    val hidden: Boolean,
)

private fun SeedMapState.pinQuery(): PinQuery {
    val world = world
    val types =
        enabledStructures.filter { it.isRegionBased && it.isAvailableIn(world) }.toSet()
    val area = viewport?.let(::pinSearchArea)
    return PinQuery(world, types, area, hidden = viewport != null && area == null)
}

/** The world to find the spawn of, or null when the dimension has none. */
private fun SeedMapState.spawnQuery(): MapWorld? = world.takeIf { it.dimension == Dimension.OVERWORLD }

/** The world to find strongholds in, or null when they are toggled off or do not exist there. */
private fun SeedMapState.strongholdQuery(): MapWorld? =
    world.takeIf { StructureType.STRONGHOLD in enabledStructures && StructureType.STRONGHOLD.isAvailableIn(it) }

/** The same state in another world; everything that belongs to the old world is dropped. */
private fun SeedMapState.inWorld(
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

private fun SeedMapState.toggled(type: StructureType): SeedMapState =
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

/**
 * The result of [block], or null if the engine refused or failed the request, which leaves the map without
 * that answer rather than taking the app down. Cancellation still propagates.
 */
private inline fun <T> attempt(block: () -> T): T? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }
