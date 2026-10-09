package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.seedmap.TileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

internal const val SEED_MAP_CANVAS_TAG = "seed-map-canvas"

private val TapRadius = 24.dp
private val SelectionRadius = 16.dp
private val CROSSHAIR_ARM = 8.dp

/**
 * The map itself: biome tiles with structure pins, spawn and strongholds on top, panned and zoomed by
 * touch. Position and zoom live in [camera], which the screen can also move (zoom buttons, "go to").
 *
 * The canvas asks [tiles] for exactly the tiles on screen and draws whatever is ready, so panning never
 * waits for generation. The view is reported to the ViewModel with [SeedMapAction.ViewportChanged]; taps
 * come back as selections.
 */
@Composable
internal fun SeedMapCanvas(
    state: SeedMapState,
    tiles: TileCache,
    camera: MapCamera,
    onAction: (SeedMapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tilesLoaded = remember { mutableIntStateOf(0) }
    val loader = remember(tiles) { TileLoader(scope, tiles, Dispatchers.Default) { tilesLoaded.intValue++ } }
    DisposableEffect(loader) { onDispose { loader.cancelAll() } }

    val world = state.world
    val wanted = remember(camera, world) { derivedStateOf { wantedTiles(camera, world) } }
    LaunchedEffect(loader, wanted) {
        snapshotFlow { wanted.value }
            .distinctUntilChanged()
            .collect { loader.request(it) }
    }

    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(camera) {
        snapshotFlow { camera.viewport() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { currentOnAction(SeedMapAction.ViewportChanged(it)) }
    }

    val currentState by rememberUpdatedState(state)
    val drawer = remember { TileDrawer() }
    val markerPath = remember { Path() }
    val background = MaterialTheme.colorScheme.surfaceVariant
    val description = stringResource(R.string.seedmap_map_description, state.seed.toString(), state.version.label)

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { camera.resize(it.width, it.height) }
                .semantics { contentDescription = description }
                .testTag(SEED_MAP_CANVAS_TAG)
                .pointerInput(camera) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        camera.zoomBy(zoom, centroid.x, centroid.y)
                        camera.panBy(pan.x, pan.y)
                    }
                }.pointerInput(camera) {
                    detectTapGestures(
                        onDoubleTap = { camera.zoomBy(DOUBLE_TAP_ZOOM, it.x, it.y) },
                        onTap = { tap ->
                            currentOnAction(selectionAt(camera, tap, currentState, TapRadius.toPx()))
                        },
                    )
                },
    ) {
        tilesLoaded.intValue // Read here so the canvas redraws when a tile lands.
        drawRect(background)
        with(drawer) { drawTiles(camera, tiles, wanted.value) }
        drawMarkers(camera, currentState, markerPath)
        drawCrosshair()
    }
}

private const val DOUBLE_TAP_ZOOM = 2f

/** What a tap at [tap] selects: a marker under the finger, otherwise the biome at that spot. */
internal fun selectionAt(
    camera: MapCamera,
    tap: Offset,
    state: SeedMapState,
    radiusPx: Float,
): SeedMapAction =
    when (
        val hit = hitTest(camera, tap, state.spawn, state.strongholds, state.pins, radiusPx)
    ) {
        is MarkerHit.Structure -> SeedMapAction.SelectStructure(hit.structure)
        MarkerHit.Spawn -> SeedMapAction.SelectSpawn
        null -> SeedMapAction.SelectPoint(camera.blockAt(tap.x, tap.y))
    }

private fun DrawScope.drawMarkers(
    camera: MapCamera,
    state: SeedMapState,
    path: Path,
) {
    val radius = MarkerRadius.toPx()
    val margin = radius * 2

    fun visible(at: Offset) = at.x in -margin..size.width + margin && at.y in -margin..size.height + margin

    fun at(pos: BlockPos) = Offset(camera.screenX(pos.x.toDouble()), camera.screenY(pos.z.toDouble()))

    state.selection?.let { selection ->
        val center = at(selection.pos)
        if (visible(center)) drawSelection(center, SelectionRadius.toPx())
    }
    state.pins.forEach { pin ->
        at(pin.pos).takeIf(::visible)?.let { drawMarker(pin.type.markerStyle(), it, radius, path) }
    }
    val strongholdStyle = StructureType.STRONGHOLD.markerStyle()
    state.strongholds.forEach { pos ->
        at(pos).takeIf(::visible)?.let { drawMarker(strongholdStyle, it, radius, path) }
    }
    state.spawn?.let { pos -> at(pos).takeIf(::visible)?.let { drawMarker(SpawnMarkerStyle, it, radius, path) } }
}

/** A small plus at the middle of the canvas, which is the point the coordinate readout names. */
private fun DrawScope.drawCrosshair() {
    val arm = CROSSHAIR_ARM.toPx()
    for ((width, color) in listOf(4.dp.toPx() to Color.Black.copy(alpha = 0.6f), 2.dp.toPx() to Color.White)) {
        drawLine(color, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), width)
        drawLine(color, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), width)
    }
}

private fun DrawScope.drawSelection(
    center: Offset,
    radius: Float,
) {
    drawCircle(Color.Black.copy(alpha = 0.7f), radius + 1.5.dp.toPx(), center, style = Stroke(5.dp.toPx()))
    drawCircle(Color.White, radius, center, style = Stroke(2.5.dp.toPx()))
}
