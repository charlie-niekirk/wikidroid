package dev.cniekirk.wikidroid.feature.seedmap

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.model.PointOfInterest
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.seedmap.StructureWikiTitles
import kotlinx.coroutines.launch

internal const val SELECTION_SHEET_TAG = "seed-map-selection"
internal const val SELECTION_TITLE_TAG = "seed-map-selection-title"
internal const val COPY_COORDINATES_TAG = "seed-map-copy"
internal const val OPEN_ARTICLE_TAG = "seed-map-open-article"
internal const val FILTER_SHEET_TAG = "seed-map-filter-sheet"
internal const val SLIME_CHUNK_TAG = "seed-map-slime-chunk"
internal const val POI_EDIT_TAG = "seed-map-poi-edit"
internal const val MEASURE_FROM_TAG = "seed-map-measure-from"
internal const val POI_SHEET_DELETE_TAG = "seed-map-poi-sheet-delete"
internal const val POI_ADD_LINKED_TAG = "seed-map-poi-add-linked"
internal const val POI_LINKED_TAG = "seed-map-poi-linked"

/** What a tap picked: its name, where it is, and the actions that follow from it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionSheet(
    selection: MapSelection,
    dimension: Dimension,
    onAction: (SeedMapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(selection) { mutableStateOf(false) }
    val point = (selection as? MapSelection.Poi)?.point
    val height = point?.y
    // With a height, in the order /tp takes them.
    val pos = selection.pos
    val title = point?.displayName() ?: selection.wikiTitle ?: stringResource(R.string.seedmap_unknown_biome)
    val coordinates = if (height != null) "${pos.x} $height ${pos.z}" else "${pos.x}, ${pos.z}"

    ModalBottomSheet(onDismissRequest = { onAction(SeedMapAction.DismissSelection) }, modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).testTag(SELECTION_SHEET_TAG),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (point != null) {
                    PoiMarker(point.category.icon, point.color, size = 40.dp)
                } else {
                    selection.icon()?.let { StructureIconImage(it, size = 40.dp) }
                }
                Text(
                    text = title,
                    modifier = Modifier.testTag(SELECTION_TITLE_TAG),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Text(
                text = coordinatesText(selection.pos, dimension),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (point != null) PoiDetails(point)
            if (selection is MapSelection.Biome && selection.slimeChunk) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WikiIcon(icon = WikiIcons.BubbleChart, contentDescription = null, tint = SlimeChunkGreen)
                    Text(
                        text = stringResource(R.string.seedmap_slime_chunk, selection.pos.chunkX, selection.pos.chunkZ),
                        modifier = Modifier.testTag(SLIME_CHUNK_TAG),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            // Stacked, because "Open wiki article" does not fit beside the other button on a phone.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Coordinates", coordinates)))
                            copied = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag(COPY_COORDINATES_TAG),
                ) {
                    WikiIcon(
                        icon = if (copied) WikiIcons.Check else WikiIcons.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        stringResource(
                            if (copied) R.string.seedmap_coordinates_copied else R.string.seedmap_copy_coordinates,
                        ),
                    )
                }
                OutlinedButton(
                    onClick = { onAction(SeedMapAction.MeasureFrom(selection.pos)) },
                    modifier = Modifier.fillMaxWidth().testTag(MEASURE_FROM_TAG),
                ) {
                    WikiIcon(
                        icon = WikiIcons.Straighten,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(R.string.seedmap_measure_from_here))
                }
                if (point != null) PoiButtons(point, onAction)
                if (selection.wikiTitle != null) {
                    Button(
                        onClick = { onAction(SeedMapAction.OpenWikiArticle) },
                        modifier = Modifier.fillMaxWidth().testTag(OPEN_ARTICLE_TAG),
                    ) {
                        WikiIcon(
                            icon = WikiIcons.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(stringResource(R.string.seedmap_open_article))
                    }
                }
            }
        }
    }
}

/** A point's height, note, and for a portal where it leads. */
@Composable
private fun PoiDetails(point: PointOfInterest) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        point.y?.let { y ->
            Text(
                text = stringResource(R.string.seedmap_poi_height, y),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        point.note?.let { note -> Text(text = note, style = MaterialTheme.typography.bodyLarge) }
        val linked = point.linkedPortal()
        val dimension = linked?.mapDimension
        if (linked != null && dimension != null) {
            val dimensionName = stringResource(dimension.labelRes())
            Text(
                text = stringResource(R.string.seedmap_poi_links_to, linked.x, linked.z, dimensionName),
                modifier = Modifier.testTag(POI_LINKED_TAG),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** Edit and delete, and for a portal, adding the one it leads to. */
@Composable
private fun PoiButtons(
    point: PointOfInterest,
    onAction: (SeedMapAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onAction(SeedMapAction.EditPoi(point)) },
                modifier = Modifier.weight(1f).testTag(POI_EDIT_TAG),
            ) {
                WikiIcon(icon = WikiIcons.Edit, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.seedmap_poi_edit))
            }
            OutlinedButton(
                onClick = { onAction(SeedMapAction.DeletePoi(point)) },
                modifier = Modifier.weight(1f).testTag(POI_SHEET_DELETE_TAG),
            ) {
                WikiIcon(icon = WikiIcons.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.seedmap_poi_delete))
            }
        }
        if (point.linkedPortal() != null) {
            Button(
                onClick = { onAction(SeedMapAction.AddLinkedPortal(point)) },
                modifier = Modifier.fillMaxWidth().testTag(POI_ADD_LINKED_TAG),
            ) {
                WikiIcon(icon = WikiIcons.Portal, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.seedmap_poi_add_linked))
            }
        }
    }
}

/**
 * One chip per structure this version and dimension can generate; the chip carries the same marker the
 * map draws, so the sheet doubles as the legend for the pins.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StructureFilterSheet(
    state: SeedMapState,
    onAction: (SeedMapAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = remember(state.world) { StructureType.availableIn(state.world) }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .testTag(FILTER_SHEET_TAG),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.seedmap_filter_title), style = MaterialTheme.typography.titleLarge)
            if (available.isEmpty()) {
                Text(stringResource(R.string.seedmap_filter_none), style = MaterialTheme.typography.bodyLarge)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach { type ->
                    FilterChip(
                        selected = type in state.enabledStructures,
                        onClick = { onAction(SeedMapAction.ToggleStructure(type)) },
                        label = { Text(StructureWikiTitles.titleFor(type)) },
                        leadingIcon = { MarkerIcon(type.markerStyle(), icon = type.icon()) },
                    )
                }
            }
        }
    }
}
