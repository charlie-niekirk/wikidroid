package dev.cniekirk.wikidroid.feature.seedmap

import androidx.lifecycle.ViewModel
import dev.cniekirk.wikidroid.core.data.PointOfInterestRepository
import dev.cniekirk.wikidroid.core.data.SeedRepository
import dev.cniekirk.wikidroid.core.domain.seedmap.BiomeAtUseCase
import dev.cniekirk.wikidroid.core.domain.seedmap.FindNearestStructureUseCase
import dev.cniekirk.wikidroid.core.domain.seedmap.FindSpawnUseCase
import dev.cniekirk.wikidroid.core.domain.seedmap.FindStrongholdsUseCase
import dev.cniekirk.wikidroid.core.domain.seedmap.FindStructuresUseCase
import dev.cniekirk.wikidroid.core.domain.seedmap.GetBiomeLegendUseCase
import dev.cniekirk.wikidroid.core.model.PointOfInterest
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
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
@Suppress("TooManyFunctions") // One intent per action that does more than reduce.
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SeedMapViewModel(
    private val seeds: SeedRepository,
    private val pois: PointOfInterestRepository,
    private val findSpawn: FindSpawnUseCase,
    private val findStrongholds: FindStrongholdsUseCase,
    private val findStructures: FindStructuresUseCase,
    private val getBiomeLegend: GetBiomeLegendUseCase,
    private val biomeAt: BiomeAtUseCase,
    private val findNearestStructure: FindNearestStructureUseCase,
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
                        .map { it.seed to it.dimension }
                        .distinctUntilChanged()
                        .flatMapLatest { (seed, dimension) ->
                            pois.observe(seed, dimension.name).map { Triple(seed, dimension, it) }
                        }.collect { (seed, dimension, found) -> reduce { state.withPois(seed, dimension, found) } }
                }
                launch {
                    container.stateFlow
                        .map { it.spawnQuery }
                        .distinctUntilChanged()
                        .collectLatest { world ->
                            val spawn = world?.let { findSpawn(it) }
                            reduce { state.withSpawn(world, spawn) }
                        }
                }
                launch {
                    container.stateFlow
                        .map { it.strongholdQuery }
                        .distinctUntilChanged()
                        .collectLatest { world ->
                            val found = world?.let { findStrongholds(it) }.orEmpty()
                            reduce { state.withStrongholds(world, found) }
                        }
                }
                launch {
                    container.stateFlow
                        .map { it.version to it.dimension }
                        .distinctUntilChanged()
                        .collectLatest { (version, dimension) ->
                            val legend = getBiomeLegend(version, dimension)
                            reduce { state.withLegend(version, dimension, legend) }
                        }
                }
                launch {
                    var previous: PinQuery? = null
                    container.stateFlow
                        .map { it.pinQuery }
                        .distinctUntilChanged()
                        .transformLatest { query ->
                            // Only a moved view is worth waiting on; a new world or toggle is answered at once.
                            if (previous?.world == query.world && previous?.types == query.types) {
                                delay(PIN_DEBOUNCE_MILLIS)
                            }
                            previous = query
                            emit(query)
                        }.collectLatest { query ->
                            val area = query.searchArea
                            val found =
                                if (area ==
                                    null
                                ) {
                                    emptyList()
                                } else {
                                    findStructures(query.world, query.types, area)
                                }
                            reduce { state.withPins(query, found) }
                        }
                }
            }
        }

    fun onAction(action: SeedMapAction) {
        when (action) {
            is SeedMapAction.SubmitSeed -> intent { reduce { state.inWorld(seed = SeedParser.parse(action.text)) } }
            is SeedMapAction.SelectVersion -> intent { reduce { state.withVersion(action.version) } }
            is SeedMapAction.SelectDimension -> intent { reduce { state.withDimension(action.dimension) } }
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
            is SeedMapAction.OpenLegendArticle -> intent { postSideEffect(SeedMapEffect.OpenArticle(action.title)) }
            SeedMapAction.GoToSpawn -> goToSpawn()
            is SeedMapAction.GoToCoordinates -> goToCoordinates(action.x, action.z)
            is SeedMapAction.GoToNearestStructure -> goToNearest(action.type)
            SeedMapAction.NearestMissingShown -> intent { reduce { state.copy(nearestMissing = null) } }
            is SeedMapAction.SetGrid -> intent { reduce { state.copy(showGrid = action.show) } }
            is SeedMapAction.SetCrosshair -> intent { reduce { state.copy(showCrosshair = action.show) } }
            is SeedMapAction.SetSlimeChunks -> intent { reduce { state.copy(showSlimeChunks = action.show) } }
            else -> onPoiAction(action)
        }
    }

    /** The actions about the player's own points. */
    private fun onPoiAction(action: SeedMapAction) {
        when (action) {
            is SeedMapAction.StartAddPoi -> intent { reduce { state.copy(poiEditor = PoiDraft(action.pos)) } }
            is SeedMapAction.EditPoi -> intent { reduce { state.withPoiEditor(action.point) } }
            SeedMapAction.DismissPoiEditor -> intent { reduce { state.copy(poiEditor = null) } }
            is SeedMapAction.SavePoi -> savePoi(action)
            is SeedMapAction.SelectPoi -> intent { reduce { state.copy(selection = MapSelection.Poi(action.point)) } }
            is SeedMapAction.GoToPoi -> goToPoi(action.point)
            is SeedMapAction.DeletePoi -> deletePoi(action.point)
            SeedMapAction.UndoDeletePoi -> undoDeletePoi()
            SeedMapAction.RemovedPoiShown -> intent { reduce { state.copy(removedPoi = null) } }
            is SeedMapAction.AddLinkedPortal -> addLinkedPortal(action.point)
            SeedMapAction.LinkedPortalShown -> intent { reduce { state.copy(linkedPortalAddedTo = null) } }
            else -> error("Not a point action: $action")
        }
    }

    /** Saves what the editor holds, as a new point in this world or over the one being edited, and closes it. */
    private fun savePoi(input: SeedMapAction.SavePoi) =
        intent {
            val draft = state.poiEditor ?: return@intent
            val base =
                draft.existing ?: PointOfInterest(seed = state.seed, dimension = state.dimension.name, x = 0, z = 0)
            pois.save(base.edited(input))
            reduce { state.copy(poiEditor = null) }
        }

    private fun goToPoi(point: PointOfInterest) =
        intent {
            reduce { state.copy(selection = MapSelection.Poi(point)) }
            postSideEffect(SeedMapEffect.CenterOn(point.pos))
        }

    private fun deletePoi(point: PointOfInterest) =
        intent {
            pois.delete(point)
            reduce { state.withoutPoi(point) }
        }

    private fun undoDeletePoi() =
        intent {
            val removed = state.removedPoi ?: return@intent
            pois.restore(removed)
            reduce { state.copy(removedPoi = null) }
        }

    /**
     * Adds the portal [point] leads to, in the other dimension of the same seed, and says where it went. The
     * point's sheet closes, or it would cover the snackbar saying so.
     */
    private fun addLinkedPortal(point: PointOfInterest) =
        intent {
            val linked = point.linkedPortal() ?: return@intent
            pois.save(linked)
            reduce { state.copy(linkedPortalAddedTo = Dimension.valueOf(linked.dimension), selection = null) }
        }

    private fun selectStructure(structure: StructurePos) =
        intent { reduce { state.copy(selection = MapSelection.Structure(structure)) } }

    private fun selectSpawn() =
        intent { reduce { state.spawn?.let { state.copy(selection = MapSelection.Spawn(it)) } ?: state } }

    /** Opens the article and closes the sheet, which would otherwise sit over the article on a wide window. */
    private fun openWikiArticle() =
        intent {
            state.selection?.wikiTitle?.let { title ->
                reduce { state.copy(selection = null) }
                postSideEffect(SeedMapEffect.OpenArticle(title))
            }
        }

    private fun goToCoordinates(
        x: Int,
        z: Int,
    ) = intent {
        val limit = SeedMapEngine.MAX_COORDINATE
        postSideEffect(SeedMapEffect.CenterOn(BlockPos(x.coerceIn(-limit, limit), z.coerceIn(-limit, limit))))
    }

    private fun saveSeed(label: String?) =
        intent {
            val current = state
            seeds.saveSeed(SavedSeed(current.seed, current.version.label, label?.trim()?.takeIf { it.isNotEmpty() }))
        }

    private fun loadSavedSeed(saved: SavedSeed) =
        intent {
            val version = McVersion.fromLabel(saved.version) ?: return@intent
            reduce { state.withVersion(version, seed = saved.seed) }
        }

    private fun selectPoint(pos: BlockPos) =
        intent {
            val world = state.world
            val biome = biomeAt(world, pos) ?: return@intent
            reduce { state.withBiomeSelection(world, pos, biome) }
        }

    /** Finds the [type] closest to the middle of the view, then selects it and centres the map on it. */
    private fun goToNearest(type: StructureType) =
        intent {
            val world = state.world
            val from = state.viewport?.let { BlockPos(it.centerX, it.centerZ) } ?: BlockPos(0, 0)
            reduce { state.copy(findingNearest = type, nearestMissing = null) }
            val found = findNearestStructure(world, type, from)
            reduce { state.withNearest(world, type, found) }
            if (found != null && state.world == world) postSideEffect(SeedMapEffect.CenterOn(found.pos))
        }

    private fun goToSpawn() =
        intent {
            val spawn = state.spawn ?: findSpawn(state.world)
            spawn?.let { postSideEffect(SeedMapEffect.CenterOn(it)) }
        }

    private companion object {
        const val PIN_DEBOUNCE_MILLIS = 200L
    }
}
