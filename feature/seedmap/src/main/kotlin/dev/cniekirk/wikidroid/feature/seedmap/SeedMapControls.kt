package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons

internal const val WORLD_CHIP_TAG = "seed-map-world-chip"
internal const val STRUCTURES_CHIP_TAG = "seed-map-structures-chip"
internal const val POIS_CHIP_TAG = "seed-map-pois-chip"

/**
 * The chips under the seed pill: the world (version and dimension, opening the layers sheet), the structures
 * shown on the map (opening the filter sheet) and the player's points (opening their list). The row scrolls
 * sideways once it holds more than fits; [contentPadding] lines its first chip up with the pill while letting chips
 * scroll to the edge.
 */
@Composable
internal fun MapChipRow(
    state: SeedMapState,
    onWorldClick: () -> Unit,
    onStructuresClick: () -> Unit,
    onPoisClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val colors =
        AssistChipDefaults.elevatedAssistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val dimension = stringResource(state.dimension.labelRes())
        val worldDescription = stringResource(R.string.seedmap_world_chip_description, state.version.label, dimension)
        ElevatedAssistChip(
            onClick = onWorldClick,
            label = { Text(stringResource(R.string.seedmap_world_chip, state.version.label, dimension)) },
            modifier = Modifier.testTag(WORLD_CHIP_TAG).semantics { contentDescription = worldDescription },
            leadingIcon = { DimensionIcon(state.dimension) },
            trailingIcon = { WikiIcon(icon = WikiIcons.ExpandMore, contentDescription = null) },
            colors = colors,
        )
        val shown = remember(state.world, state.enabledStructures) { state.shownStructureCount() }
        ElevatedAssistChip(
            onClick = onStructuresClick,
            label = { Text(stringResource(R.string.seedmap_structures_chip, shown)) },
            modifier = Modifier.testTag(STRUCTURES_CHIP_TAG),
            leadingIcon = {
                WikiIcon(
                    icon = WikiIcons.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = { WikiIcon(icon = WikiIcons.ExpandMore, contentDescription = null) },
            colors = colors,
        )
        ElevatedAssistChip(
            onClick = onPoisClick,
            label = { Text(stringResource(R.string.seedmap_pois_chip, state.pois.size)) },
            modifier = Modifier.testTag(POIS_CHIP_TAG),
            leadingIcon = {
                WikiIcon(
                    icon = WikiIcons.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = { WikiIcon(icon = WikiIcons.ExpandMore, contentDescription = null) },
            colors = colors,
        )
    }
}

/** How many of the toggled-on structures this version and dimension can generate. */
internal fun SeedMapState.shownStructureCount(): Int = enabledStructures.count { it.isAvailableIn(world) }

internal const val SAVE_NAME_TAG = "seed-map-save-name"
internal const val SAVE_CONFIRM_TAG = "seed-map-save-confirm"

/** Asks for an optional name for the seed being saved. */
@Composable
internal fun SaveSeedDialog(
    onSave: (label: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val name = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.seedmap_save_dialog_title)) },
        text = {
            OutlinedTextField(
                state = name,
                modifier = Modifier.fillMaxWidth().testTag(SAVE_NAME_TAG),
                label = { Text(stringResource(R.string.seedmap_save_dialog_name)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onKeyboardAction = KeyboardActionHandler { onSave(name.text.toString()) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.text.toString()) }, modifier = Modifier.testTag(SAVE_CONFIRM_TAG)) {
                Text(stringResource(R.string.seedmap_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.seedmap_cancel)) } },
    )
}
