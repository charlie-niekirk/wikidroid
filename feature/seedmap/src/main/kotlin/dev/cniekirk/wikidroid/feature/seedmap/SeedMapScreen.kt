package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiTopAppBar
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.TileCache
import kotlin.math.roundToInt

internal const val GO_TO_SPAWN_TAG = "seed-map-go-to-spawn"

/** The dialogs and sheets the screen can show. Kept as one value so only one is ever open. */
private enum class Overlay { None, Filter, SaveSeed, GoToCoordinates }

/**
 * The Seed map tab: a pannable map of the seed with the seed field and version picker above it.
 * Stateless: the route owns the ViewModel, the tile cache and navigation. Where the map is looking is
 * held by [camera], which the route also moves when the ViewModel asks it to go somewhere.
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

    Scaffold(
        modifier = modifier,
        topBar = {
            WikiTopAppBar(
                title = stringResource(R.string.seedmap_title),
                actions = {
                    SavedSeedsMenu(
                        saved = state.savedSeeds,
                        isCurrentSaved = state.isSaved,
                        onSaveCurrent = { overlay = Overlay.SaveSeed },
                        onAction = onAction,
                    )
                    StructureFilterButton(onClick = { overlay = Overlay.Filter })
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SeedControls(
                seed = state.seed,
                version = state.version,
                onAction = onAction,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SeedMapCanvas(state = state, tiles = tiles, camera = camera, onAction = onAction)
                MapOverlay(
                    state = state,
                    camera = camera,
                    onGoToCoordinates = { overlay = Overlay.GoToCoordinates },
                    onAction = onAction,
                )
            }
        }
    }

    when (overlay) {
        Overlay.None -> {
            Unit
        }

        Overlay.Filter -> {
            StructureFilterSheet(state = state, onAction = onAction, onDismiss = { overlay = Overlay.None })
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

        Overlay.GoToCoordinates -> {
            GoToCoordinatesDialog(
                onGo = { x, z ->
                    onAction(SeedMapAction.GoToCoordinates(x, z))
                    overlay = Overlay.None
                },
                onDismiss = { overlay = Overlay.None },
            )
        }
    }

    state.selection?.let { selection ->
        SelectionSheet(selection = selection, onAction = onAction)
    }
}

/** Zoom buttons, "go to" buttons, the coordinate readout and the hint shown while pins are hidden. */
@Composable
private fun MapOverlay(
    state: SeedMapState,
    camera: MapCamera,
    onGoToCoordinates: () -> Unit,
    onAction: (SeedMapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.pinsHidden) {
            MapChip(
                text = stringResource(R.string.seedmap_pins_hidden),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )
        }
        MapChip(
            text =
                stringResource(
                    R.string.seedmap_coordinates,
                    camera.centerX.roundToInt(),
                    camera.centerZ.roundToInt(),
                ),
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).testTag(CENTER_READOUT_TAG),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapButton(
                icon = WikiIcons.PinDrop,
                description = stringResource(R.string.seedmap_go_to_coordinates),
                onClick = onGoToCoordinates,
            )
            if (state.dimension == Dimension.OVERWORLD) {
                MapButton(
                    icon = WikiIcons.MyLocation,
                    description = stringResource(R.string.seedmap_go_to_spawn),
                    onClick = { onAction(SeedMapAction.GoToSpawn) },
                    modifier = Modifier.testTag(GO_TO_SPAWN_TAG),
                )
            }
            MapButton(
                icon = WikiIcons.Add,
                description = stringResource(R.string.seedmap_zoom_in),
                onClick = { camera.zoomBy(ZOOM_STEP) },
            )
            MapButton(
                icon = WikiIcons.Remove,
                description = stringResource(R.string.seedmap_zoom_out),
                onClick = { camera.zoomBy(1f / ZOOM_STEP) },
            )
        }
    }
}

internal const val CENTER_READOUT_TAG = "seed-map-center"
private const val ZOOM_STEP = 2f

@Composable
private fun MapButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
    ) {
        WikiIcon(icon = icon, contentDescription = description)
    }
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
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

internal fun SavedSeed.displayName(): String = label ?: seed.toString()
