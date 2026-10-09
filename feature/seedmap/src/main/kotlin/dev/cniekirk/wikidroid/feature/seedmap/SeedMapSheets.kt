package dev.cniekirk.wikidroid.feature.seedmap

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.seedmap.StructureWikiTitles
import kotlinx.coroutines.launch

internal const val SELECTION_SHEET_TAG = "seed-map-selection"
internal const val SELECTION_TITLE_TAG = "seed-map-selection-title"
internal const val COPY_COORDINATES_TAG = "seed-map-copy"
internal const val OPEN_ARTICLE_TAG = "seed-map-open-article"
internal const val FILTER_SHEET_TAG = "seed-map-filter-sheet"

/** What a tap picked: its name, where it is, and the actions that follow from it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionSheet(
    selection: MapSelection,
    onAction: (SeedMapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(selection) { mutableStateOf(false) }
    val coordinates = "${selection.pos.x}, ${selection.pos.z}"

    ModalBottomSheet(onDismissRequest = { onAction(SeedMapAction.DismissSelection) }, modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).testTag(SELECTION_SHEET_TAG),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = selection.wikiTitle ?: stringResource(R.string.seedmap_unknown_biome),
                modifier = Modifier.testTag(SELECTION_TITLE_TAG),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.seedmap_coordinates, selection.pos.x, selection.pos.z),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Coordinates", coordinates)))
                            copied = true
                        }
                    },
                    modifier = Modifier.testTag(COPY_COORDINATES_TAG),
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
                if (selection.wikiTitle != null) {
                    Button(
                        onClick = { onAction(SeedMapAction.OpenWikiArticle) },
                        modifier = Modifier.testTag(OPEN_ARTICLE_TAG),
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
                        leadingIcon = { MarkerIcon(type.markerStyle()) },
                    )
                }
            }
        }
    }
}
