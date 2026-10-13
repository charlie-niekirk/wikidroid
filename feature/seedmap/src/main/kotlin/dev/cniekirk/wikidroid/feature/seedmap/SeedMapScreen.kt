package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIconButton
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.domain.seedmap.FindNearestStructureUseCase
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.seedmap.StructureWikiTitles
import dev.cniekirk.wikidroid.core.seedmap.TileCache
import kotlin.math.roundToInt

internal const val GO_TO_SPAWN_TAG = "seed-map-go-to-spawn"
internal const val MAP_TOOLBAR_TAG = "seed-map-toolbar"
internal const val FINDING_CHIP_TAG = "seed-map-finding"

/** The dialogs and sheets the screen can show. Kept as one value so only one is ever open. */
private enum class Overlay { None, Filter, Legend, Layers, SaveSeed }

/**
 * The Seed map tab: the map from edge to edge, with the seed pill and chips floating over its top and the
 * coordinate readout and map toolbar over its bottom. Stateless: the route owns the ViewModel, the tile cache and
 * navigation. Where the map is looking is held by [camera], which the route also moves when the ViewModel asks it
 * to go somewhere.
 *
 * The map draws behind the status bar; everything floating keeps clear of it (and of a cutout or the navigation
 * rail) through the safe drawing insets. The app's navigation bar has already consumed the bottom inset.
 */
@Composable
fun SeedMapScreen(
    state: SeedMapState,
    tiles: TileCache,
    onAction: (SeedMapAction) -> Unit,
    modifier: Modifier = Modifier,
    camera: MapCamera = rememberMapCamera(),
) {
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
    val snackbarHostState = remember { SnackbarHostState() }
    KeepViewAcrossDimensions(state.dimension, camera)

    Box(modifier = modifier.fillMaxSize()) {
        SeedMapCanvas(state = state, tiles = tiles, camera = camera, onAction = onAction)
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val sides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            SeedSearchBar(
                state = state,
                onSave = { overlay = Overlay.SaveSeed },
                onLegend = { overlay = Overlay.Legend },
                onAction = onAction,
                modifier = Modifier.windowInsetsPadding(sides).padding(horizontal = 16.dp),
            )
            MapChipRow(
                state = state,
                onWorldClick = { overlay = Overlay.Layers },
                onStructuresClick = { overlay = Overlay.Filter },
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(sides).padding(top = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )
            val finding = state.findingNearest
            if (finding != null) {
                FindingChip(type = finding, modifier = Modifier.padding(top = 8.dp))
            } else if (state.pinsHidden) {
                MapChip(text = stringResource(R.string.seedmap_pins_hidden), modifier = Modifier.padding(top = 8.dp))
            }
        }
        val bottomInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        MapOverlay(
            state = state,
            camera = camera,
            onAction = onAction,
            modifier = Modifier.windowInsetsPadding(bottomInsets),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(bottomInsets),
        )
    }
    NearestMissingSnackbar(state.nearestMissing, snackbarHostState, onAction)

    when (overlay) {
        Overlay.None -> {
            Unit
        }

        Overlay.Filter -> {
            StructureFilterSheet(state = state, onAction = onAction, onDismiss = { overlay = Overlay.None })
        }

        Overlay.Legend -> {
            LegendSheet(
                state = state,
                onOpenArticle = { title ->
                    onAction(SeedMapAction.OpenLegendArticle(title))
                    overlay = Overlay.None
                },
                onDismiss = { overlay = Overlay.None },
            )
        }

        Overlay.Layers -> {
            LayersSheet(state = state, onAction = onAction, onDismiss = { overlay = Overlay.None })
        }

        Overlay.SaveSeed -> {
            SaveSeedDialog(
                onSave = { label ->
                    onAction(SeedMapAction.SaveSeed(label))
                    overlay = Overlay.None
                },
                onDismiss = { overlay = Overlay.None },
            )
        }
    }

    state.selection?.let { selection ->
        SelectionSheet(selection = selection, dimension = state.dimension, onAction = onAction)
    }
}

/** Moves the camera with the player when the dimension changes (the Nether is 1:8), then remembers it. */
@Composable
private fun KeepViewAcrossDimensions(
    dimension: Dimension,
    camera: MapCamera,
) {
    var shown by remember { mutableStateOf(dimension) }
    LaunchedEffect(dimension) {
        if (dimension != shown) {
            camera.convertDimension(shown, dimension)
            shown = dimension
        }
    }
}

/** Says that no [missing] structure was within reach, then acknowledges it once the snackbar has gone. */
@Composable
private fun NearestMissingSnackbar(
    missing: StructureType?,
    snackbarHostState: SnackbarHostState,
    onAction: (SeedMapAction) -> Unit,
) {
    val text =
        missing?.let {
            stringResource(
                R.string.seedmap_nearest_missing,
                StructureWikiTitles.titleFor(it),
                "%,d".format(FindNearestStructureUseCase.REACH_BLOCKS),
            )
        }
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(missing) {
        if (text == null) return@LaunchedEffect
        // Acknowledged only once it has been shown: clearing it earlier would cancel this effect.
        snackbarHostState.showSnackbar(text)
        currentOnAction(SeedMapAction.NearestMissingShown)
    }
}

/** "Finding the nearest Stronghold" with a spinner, while the search looks. */
@Composable
private fun FindingChip(
    type: StructureType,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag(FINDING_CHIP_TAG).semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.seedmap_finding_nearest, StructureWikiTitles.titleFor(type)),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * A fade from the surface colour behind the status bar, so its icons (dark on a light theme, light on a dark one)
 * stay readable over a snowy or a deep-ocean map.
 */
@Composable
private fun StatusBarScrim(modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(
                    Brush.verticalGradient(listOf(surface.copy(alpha = SCRIM_ALPHA), surface.copy(alpha = 0f))),
                ),
    )
}

/**
 * The coordinate readout at the bottom start and the toolbar (spawn, zoom) at the bottom end. The toolbar stands
 * up the side of a tall map and lies along the bottom of a short one (a phone on its side), where standing it would
 * reach the chips.
 */
@Composable
private fun MapOverlay(
    state: SeedMapState,
    camera: MapCamera,
    onAction: (SeedMapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val center = BlockPos(camera.centerX.roundToInt(), camera.centerZ.roundToInt())
        MapChip(
            text = coordinatesText(center, state.dimension),
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).testTag(CENTER_READOUT_TAG),
        )
        val toolbarModifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag(MAP_TOOLBAR_TAG)
        if (maxHeight >= TallMapHeight) {
            VerticalFloatingToolbar(expanded = true, modifier = toolbarModifier) {
                MapToolbarButtons(state.dimension, camera, onAction)
            }
        } else {
            HorizontalFloatingToolbar(expanded = true, modifier = toolbarModifier) {
                MapToolbarButtons(state.dimension, camera, onAction)
            }
        }
    }
}

@Composable
private fun MapToolbarButtons(
    dimension: Dimension,
    camera: MapCamera,
    onAction: (SeedMapAction) -> Unit,
) {
    if (dimension == Dimension.OVERWORLD) {
        WikiIconButton(
            icon = WikiIcons.MyLocation,
            contentDescription = stringResource(R.string.seedmap_go_to_spawn),
            onClick = { onAction(SeedMapAction.GoToSpawn) },
            modifier = Modifier.testTag(GO_TO_SPAWN_TAG),
        )
    }
    WikiIconButton(
        icon = WikiIcons.Add,
        contentDescription = stringResource(R.string.seedmap_zoom_in),
        onClick = { camera.zoomBy(ZOOM_STEP) },
    )
    WikiIconButton(
        icon = WikiIcons.Remove,
        contentDescription = stringResource(R.string.seedmap_zoom_out),
        onClick = { camera.zoomBy(1f / ZOOM_STEP) },
    )
}

/** Below this the standing toolbar would reach up to the chips under the seed pill. */
private val TallMapHeight = 480.dp

internal const val CENTER_READOUT_TAG = "seed-map-center"
private const val ZOOM_STEP = 2f
private const val SCRIM_ALPHA = 0.7f

/** "X 12, Z -3", and under it the overworld position when the map is of the Nether. */
@Composable
internal fun coordinatesText(
    pos: BlockPos,
    dimension: Dimension,
): String {
    val own = stringResource(R.string.seedmap_coordinates, pos.x, pos.z)
    val overworld = pos.overworldEquivalent(dimension) ?: return own
    return own + "\n" + stringResource(R.string.seedmap_coordinates_overworld, overworld.x, overworld.z)
}

@Composable
private fun MapChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

internal fun SavedSeed.displayName(): String = label ?: seed.toString()
