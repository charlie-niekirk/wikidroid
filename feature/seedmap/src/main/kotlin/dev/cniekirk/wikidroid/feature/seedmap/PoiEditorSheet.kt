package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.model.PoiCategory
import dev.cniekirk.wikidroid.core.model.PoiColor

internal const val POI_EDITOR_TAG = "seed-map-poi-editor"
internal const val POI_NAME_TAG = "seed-map-poi-name"
internal const val POI_X_TAG = "seed-map-poi-x"
internal const val POI_Y_TAG = "seed-map-poi-y"
internal const val POI_Z_TAG = "seed-map-poi-z"
internal const val POI_NOTE_TAG = "seed-map-poi-note"
internal const val POI_SAVE_TAG = "seed-map-poi-save"
internal const val POI_DELETE_TAG = "seed-map-poi-delete"

internal fun poiCategoryTag(category: PoiCategory): String = "seed-map-poi-category-${category.name}"

internal fun poiColorTag(color: PoiColor): String = "seed-map-poi-color-${color.name}"

/**
 * Adds a point where the map was long-pressed, or changes one. The text lives here, in the sheet, until Save sends
 * it all at once. The colour follows the kind until the player picks one. Save waits for whole-number X and Z (and
 * Y, if given).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PoiEditorSheet(
    draft: PoiDraft,
    onAction: (SeedMapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val existing = draft.existing
    val name = rememberTextFieldState(existing?.name.orEmpty())
    val note = rememberTextFieldState(existing?.note.orEmpty())
    val x = rememberTextFieldState(draft.pos.x.toString())
    val z = rememberTextFieldState(draft.pos.z.toString())
    val y = rememberTextFieldState(existing?.y?.toString().orEmpty())
    var category by rememberSaveable { mutableStateOf(existing?.category ?: PoiCategory.BASE) }
    var pickedColor by rememberSaveable { mutableStateOf(existing?.color) }
    val color = pickedColor ?: category.defaultColor

    val blockX =
        x.text
            .toString()
            .trim()
            .toIntOrNull()
    val blockZ =
        z.text
            .toString()
            .trim()
            .toIntOrNull()
    val heightText = y.text.toString().trim()
    val height = heightText.toIntOrNull()
    val valid = blockX != null && blockZ != null && (heightText.isEmpty() || height != null)

    ModalBottomSheet(
        onDismissRequest = { onAction(SeedMapAction.DismissPoiEditor) },
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .testTag(POI_EDITOR_TAG),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PoiMarker(category.icon, color)
                Text(
                    text =
                        stringResource(
                            if (existing == null) R.string.seedmap_poi_add_title else R.string.seedmap_poi_edit_title,
                        ),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            OutlinedTextField(
                state = name,
                modifier = Modifier.fillMaxWidth().testTag(POI_NAME_TAG),
                label = { Text(stringResource(R.string.seedmap_poi_name)) },
                placeholder = { Text(stringResource(R.string.seedmap_poi_name_hint, stringResource(category.label))) },
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            SectionLabel(R.string.seedmap_poi_category)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PoiCategory.entries.forEach { option ->
                    FilterChip(
                        selected = option == category,
                        onClick = { category = option },
                        label = { Text(stringResource(option.label)) },
                        modifier = Modifier.testTag(poiCategoryTag(option)),
                        leadingIcon = { WikiIcon(icon = option.icon, contentDescription = null) },
                    )
                }
            }
            SectionLabel(R.string.seedmap_poi_color)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PoiColor.entries.forEach { option ->
                    ColorSwatch(option, selected = option == color, onClick = { pickedColor = option })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoordinateField(x, R.string.seedmap_poi_x, POI_X_TAG, Modifier.weight(1f))
                CoordinateField(y, R.string.seedmap_poi_y, POI_Y_TAG, Modifier.weight(1f))
                CoordinateField(z, R.string.seedmap_poi_z, POI_Z_TAG, Modifier.weight(1f))
            }
            OutlinedTextField(
                state = note,
                modifier = Modifier.fillMaxWidth().testTag(POI_NOTE_TAG),
                label = { Text(stringResource(R.string.seedmap_poi_note)) },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = NOTE_MAX_LINES),
            )
            Button(
                onClick = {
                    if (blockX != null && blockZ != null) {
                        onAction(
                            SeedMapAction.SavePoi(
                                name = name.text.toString(),
                                category = category,
                                color = color,
                                x = blockX,
                                z = blockZ,
                                y = height,
                                note = note.text.toString(),
                            ),
                        )
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().testTag(POI_SAVE_TAG),
            ) {
                Text(stringResource(R.string.seedmap_save))
            }
            if (existing != null) {
                OutlinedButton(
                    onClick = { onAction(SeedMapAction.DeletePoi(existing)) },
                    modifier = Modifier.fillMaxWidth().testTag(POI_DELETE_TAG),
                ) {
                    WikiIcon(
                        icon = WikiIcons.Delete,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(R.string.seedmap_poi_delete))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun CoordinateField(
    state: TextFieldState,
    label: Int,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val isNumber =
        state.text.isEmpty() || state.text
            .toString()
            .trim()
            .toIntOrNull() != null
    OutlinedTextField(
        state = state,
        modifier = modifier.testTag(tag),
        label = { Text(stringResource(label)) },
        isError = !isNumber,
        lineLimits = TextFieldLineLimits.SingleLine,
        // Not KeyboardType.Number: many number pads have no minus key, and coordinates are often negative.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
}

@Composable
private fun ColorSwatch(
    color: PoiColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(color.label)
    PoiMarker(
        icon = if (selected) WikiIcons.Check else null,
        color = color,
        modifier =
            Modifier
                .testTag(poiColorTag(color))
                .clip(CircleShape)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .semantics {
                    contentDescription = label
                    this.selected = selected
                },
        size = 36.dp,
    )
}

private const val NOTE_MAX_LINES = 4
