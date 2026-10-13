package dev.cniekirk.wikidroid.feature.seedmap

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.McVersion

internal const val LAYERS_SHEET_TAG = "seed-map-layers-sheet"
internal const val VERSION_FIELD_TAG = "seed-map-version-field"
internal const val VERSION_MENU_TAG = "seed-map-version-menu"
internal const val GRID_SWITCH_TAG = "seed-map-grid"
internal const val CROSSHAIR_SWITCH_TAG = "seed-map-crosshair"

internal fun versionItemTag(version: McVersion): String = "seed-map-version-${version.name}"

/** Newest first, which is the order a player looking for a current seed wants. */
internal val VersionsNewestFirst: List<McVersion> = McVersion.entries.reversed()

/**
 * What the map shows, opened from the World chip: the dimension as three cards, the version, and the grid and
 * crosshair switches. Choices apply at once and the sheet stays open, so the map behind it can be watched
 * changing; dimensions the version lacks are greyed out with the version that added them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LayersSheet(
    state: SeedMapState,
    onAction: (SeedMapAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp).testTag(LAYERS_SHEET_TAG),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SheetTitle(R.string.seedmap_layers_title)
            SectionHeader(R.string.seedmap_layers_dimension)
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Dimension.entries.forEach { dimension ->
                    DimensionCard(
                        dimension = dimension,
                        selected = dimension == state.dimension,
                        supported = state.version.supports(dimension),
                        onClick = { onAction(SeedMapAction.SelectDimension(dimension)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            SectionHeader(R.string.seedmap_layers_version)
            VersionPicker(
                selected = state.version,
                onSelect = { onAction(SeedMapAction.SelectVersion(it)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            SectionHeader(R.string.seedmap_layers_display)
            DisplaySwitch(
                title = R.string.seedmap_layers_grid,
                summary = R.string.seedmap_layers_grid_summary,
                icon = WikiIcons.Grid,
                checked = state.showGrid,
                onCheckedChange = { onAction(SeedMapAction.SetGrid(it)) },
                tag = GRID_SWITCH_TAG,
            )
            DisplaySwitch(
                title = R.string.seedmap_layers_crosshair,
                summary = R.string.seedmap_layers_crosshair_summary,
                icon = WikiIcons.Add,
                checked = state.showCrosshair,
                onCheckedChange = { onAction(SeedMapAction.SetCrosshair(it)) },
                tag = CROSSHAIR_SWITCH_TAG,
            )
        }
    }
}

/** A tile of the dimension's ground with its sprite on it, outlined and ticked when it is the one on the map. */
@Composable
private fun DimensionCard(
    dimension: Dimension,
    selected: Boolean,
    supported: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(dimension.labelRes())
    Column(
        modifier = modifier.alpha(if (supported) 1f else DISABLED_ALPHA),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            selected = selected,
            onClick = onClick,
            enabled = supported,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(CARD_ASPECT_RATIO)
                    .testTag(dimensionItemTag(dimension))
                    .semantics { contentDescription = label },
            shape = MaterialTheme.shapes.medium,
            color = dimension.cardColor(),
            border =
                if (selected) {
                    BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                DimensionIcon(dimension, size = 32.dp)
                if (selected) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        WikiIcon(
                            icon = WikiIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.padding(2.dp).size(16.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (!supported) {
            Text(
                text = stringResource(R.string.seedmap_dimension_from, dimension.firstVersion().label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Every version, newest first, in a menu under a read-only field showing the current one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionPicker(
    selected: McVersion,
    onSelect: (McVersion) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = stringResource(R.string.seedmap_java_version, selected.label),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .testTag(VERSION_FIELD_TAG),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.testTag(VERSION_MENU_TAG),
        ) {
            VersionsNewestFirst.forEach { version ->
                DropdownMenuItem(
                    text = { Text(version.label) },
                    onClick = {
                        expanded = false
                        onSelect(version)
                    },
                    modifier = Modifier.testTag(versionItemTag(version)),
                    trailingIcon =
                        if (version == selected) {
                            { WikiIcon(icon = WikiIcons.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun DisplaySwitch(
    @StringRes title: Int,
    @StringRes summary: Int,
    icon: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
) {
    ListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.padding(horizontal = 8.dp).testTag(tag),
        leadingContent = { WikiIcon(icon = icon, contentDescription = null) },
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) {
        Text(stringResource(title))
    }
}

@Composable
private fun SectionHeader(
    @StringRes title: Int,
) {
    Text(
        text = stringResource(title),
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun SheetTitle(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(title),
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleLarge,
    )
}

private const val DISABLED_ALPHA = 0.38f
private const val CARD_ASPECT_RATIO = 1.4f
