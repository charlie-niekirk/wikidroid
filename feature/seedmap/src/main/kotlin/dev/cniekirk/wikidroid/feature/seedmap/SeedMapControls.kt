package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIconButton
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.SeedParser
import kotlinx.collections.immutable.ImmutableList

internal const val SEED_FIELD_TAG = "seed-map-seed-field"
internal const val VERSION_PICKER_TAG = "seed-map-version"
internal const val SAVED_SEEDS_TAG = "seed-map-saved-seeds"
internal const val FILTER_BUTTON_TAG = "seed-map-filter"
internal const val RANDOM_SEED_TAG = "seed-map-random"

/** Newest first, which is the order a player looking for a current seed wants. */
private val VersionsNewestFirst: List<McVersion> = McVersion.entries.reversed()

/**
 * The seed field and the version picker. The text lives in a [TextFieldState] here, per the Search
 * screen's gotcha, and is only sent when submitted. When the seed changes from outside (a saved seed, the
 * random button) the field shows the new number; text that already parses to the current seed is left alone,
 * so a seed typed as words is not replaced by its hash.
 */
@Composable
internal fun SeedControls(
    seed: Long,
    version: McVersion,
    onAction: (SeedMapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberTextFieldState(initialText = seed.toString())
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(seed) {
        val typed = text.text.toString().trim()
        if (typed.isEmpty() || SeedParser.parse(typed) != seed) text.setTextAndPlaceCursorAtEnd(seed.toString())
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            state = text,
            modifier = Modifier.weight(1f).testTag(SEED_FIELD_TAG),
            label = { Text(stringResource(R.string.seedmap_seed_label)) },
            trailingIcon = {
                WikiIconButton(
                    icon = WikiIcons.Shuffle,
                    contentDescription = stringResource(R.string.seedmap_random_seed),
                    onClick = {
                        focusManager.clearFocus()
                        onAction(SeedMapAction.SubmitSeed(""))
                    },
                    modifier = Modifier.testTag(RANDOM_SEED_TAG),
                )
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Go),
            onKeyboardAction =
                KeyboardActionHandler {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onAction(SeedMapAction.SubmitSeed(text.text.toString()))
                },
        )
        VersionPicker(
            selected = version,
            onSelect = { onAction(SeedMapAction.SelectVersion(it)) },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun VersionPicker(
    selected: McVersion,
    onSelect: (McVersion) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.testTag(VERSION_PICKER_TAG)) {
            Text(stringResource(R.string.seedmap_version_button, selected.label))
            WikiIcon(icon = WikiIcons.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VersionsNewestFirst.forEach { version ->
                DropdownMenuItem(
                    text = { Text(version.label) },
                    onClick = {
                        expanded = false
                        onSelect(version)
                    },
                    trailingIcon =
                        if (version == selected) {
                            { WikiIcon(icon = WikiIcons.Check, contentDescription = null) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

/** The bookmark button in the top bar: save this seed, and open or remove the saved ones. */
@Composable
internal fun SavedSeedsMenu(
    saved: ImmutableList<SavedSeed>,
    isCurrentSaved: Boolean,
    onSaveCurrent: () -> Unit,
    onAction: (SeedMapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        WikiIconButton(
            icon = if (isCurrentSaved) WikiIcons.Bookmark else WikiIcons.BookmarkBorder,
            contentDescription = stringResource(R.string.seedmap_saved_seeds),
            onClick = { expanded = true },
            modifier = Modifier.testTag(SAVED_SEEDS_TAG),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isCurrentSaved) R.string.seedmap_seed_is_saved else R.string.seedmap_save_seed,
                        ),
                    )
                },
                enabled = !isCurrentSaved,
                leadingIcon = { WikiIcon(icon = WikiIcons.BookmarkBorder, contentDescription = null) },
                onClick = {
                    expanded = false
                    onSaveCurrent()
                },
            )
            HorizontalDivider()
            if (saved.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.seedmap_no_saved_seeds)) },
                    enabled = false,
                    onClick = {},
                )
            }
            saved.forEach { entry ->
                SavedSeedItem(
                    entry = entry,
                    onLoad = {
                        expanded = false
                        onAction(SeedMapAction.LoadSavedSeed(entry))
                    },
                    onRemove = { onAction(SeedMapAction.DeleteSavedSeed(entry)) },
                )
            }
        }
    }
}

@Composable
private fun SavedSeedItem(
    entry: SavedSeed,
    onLoad: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(entry.displayName(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.seedmap_saved_seed_subtitle, entry.seed.toString(), entry.version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onLoad,
        trailingIcon = {
            WikiIconButton(
                icon = WikiIcons.Delete,
                contentDescription = stringResource(R.string.seedmap_remove_saved_seed, entry.displayName()),
                onClick = onRemove,
            )
        },
    )
}

@Composable
internal fun StructureFilterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WikiIconButton(
        icon = WikiIcons.Tune,
        contentDescription = stringResource(R.string.seedmap_filter_open),
        onClick = onClick,
        modifier = modifier.testTag(FILTER_BUTTON_TAG),
    )
}

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

internal const val GOTO_X_TAG = "seed-map-goto-x"
internal const val GOTO_Z_TAG = "seed-map-goto-z"
internal const val GOTO_CONFIRM_TAG = "seed-map-goto-confirm"

/** Two number fields; "Go" is enabled once both hold a whole number. */
@Composable
internal fun GoToCoordinatesDialog(
    onGo: (x: Int, z: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val x = rememberTextFieldState()
    val z = rememberTextFieldState()
    val xValue =
        x.text
            .toString()
            .trim()
            .toIntOrNull()
    val zValue =
        z.text
            .toString()
            .trim()
            .toIntOrNull()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.seedmap_goto_dialog_title)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoordinateField(
                    x,
                    R.string.seedmap_goto_x,
                    GOTO_X_TAG,
                    ImeAction.Next,
                    Modifier.weight(1f).focusRequester(focus),
                )
                CoordinateField(z, R.string.seedmap_goto_z, GOTO_Z_TAG, ImeAction.Go, Modifier.weight(1f)) {
                    if (xValue != null && zValue != null) onGo(xValue, zValue)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (xValue != null && zValue != null) onGo(xValue, zValue) },
                enabled = xValue != null && zValue != null,
                modifier = Modifier.testTag(GOTO_CONFIRM_TAG),
            ) {
                Text(stringResource(R.string.seedmap_goto_go))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.seedmap_cancel)) } },
    )
}

@Composable
private fun CoordinateField(
    state: TextFieldState,
    label: Int,
    tag: String,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    onGo: () -> Unit = {},
) {
    OutlinedTextField(
        state = state,
        modifier = modifier.testTag(tag),
        label = { Text(stringResource(label)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        onKeyboardAction = KeyboardActionHandler { onGo() },
    )
}
