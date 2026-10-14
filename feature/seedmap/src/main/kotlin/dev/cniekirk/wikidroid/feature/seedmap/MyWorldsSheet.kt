package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIconButton
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.model.SavedSeed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

internal const val WORLDS_LIST_TAG = "seed-map-worlds"
internal const val WORLDS_EMPTY_TAG = "seed-map-worlds-empty"
internal const val REMOVE_CONFIRM_TAG = "seed-map-remove-confirm"

internal fun worldRowTag(saved: SavedSeed): String = "seed-map-world-${saved.seed}-${saved.version}"

internal fun worldRenameTag(saved: SavedSeed): String = "seed-map-world-rename-${saved.seed}-${saved.version}"

internal fun worldRemoveTag(saved: SavedSeed): String = "seed-map-world-remove-${saved.seed}-${saved.version}"

/**
 * The saved seeds, each with its version and how many points it has, the one on the map marked. Tapping one
 * switches to it and closes the sheet; each can be renamed in place or removed (after asking, and keeping its
 * points, which belong to the seed rather than the saved entry).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyWorldsSheet(
    saved: ImmutableList<SavedSeed>,
    current: SavedSeed?,
    poiCounts: ImmutableMap<Long, Int>,
    onAction: (SeedMapAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf<SavedSeed?>(null) }
    var removing by remember { mutableStateOf<SavedSeed?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        SheetTitle(R.string.seedmap_worlds_title)
        if (saved.isEmpty()) {
            Text(
                text = stringResource(R.string.seedmap_worlds_empty),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 24.dp,
                            vertical = 16.dp,
                        ).testTag(WORLDS_EMPTY_TAG),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(modifier = Modifier.padding(bottom = 16.dp).testTag(WORLDS_LIST_TAG)) {
            items(saved, key = ::worldRowTag) { world ->
                WorldRow(
                    saved = world,
                    isCurrent = world == current,
                    points = poiCounts[world.seed] ?: 0,
                    onLoad = {
                        onAction(SeedMapAction.LoadSavedSeed(world))
                        onDismiss()
                    },
                    onRename = { renaming = world },
                    onRemove = { removing = world },
                )
            }
        }
    }

    renaming?.let { world ->
        SaveSeedDialog(
            onSave = { label ->
                onAction(SeedMapAction.RenameSavedSeed(world, label.orEmpty()))
                renaming = null
            },
            onDismiss = { renaming = null },
            initialName = world.label.orEmpty(),
            title = R.string.seedmap_rename_dialog_title,
        )
    }
    removing?.let { world ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.seedmap_remove_dialog_title, world.displayName())) },
            text = { Text(stringResource(R.string.seedmap_remove_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(SeedMapAction.DeleteSavedSeed(world))
                        removing = null
                    },
                    modifier = Modifier.testTag(REMOVE_CONFIRM_TAG),
                ) {
                    Text(stringResource(R.string.seedmap_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.seedmap_cancel)) }
            },
        )
    }
}

@Composable
private fun WorldRow(
    saved: SavedSeed,
    isCurrent: Boolean,
    points: Int,
    onLoad: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    val name = saved.displayName()
    ListItem(
        onClick = onLoad,
        modifier = Modifier.padding(horizontal = 8.dp).testTag(worldRowTag(saved)),
        leadingContent = {
            WikiIcon(
                icon = if (isCurrent) WikiIcons.Check else WikiIcons.Bookmark,
                contentDescription = if (isCurrent) stringResource(R.string.seedmap_world_current) else null,
            )
        },
        supportingContent = {
            Text(
                stringResource(R.string.seedmap_saved_seed_subtitle, saved.seed.toString(), saved.version) + " · " +
                    pluralStringResource(R.plurals.seedmap_world_points, points, points),
            )
        },
        trailingContent = {
            Row {
                WikiIconButton(
                    icon = WikiIcons.Edit,
                    contentDescription = stringResource(R.string.seedmap_world_rename, name),
                    onClick = onRename,
                    modifier = Modifier.testTag(worldRenameTag(saved)),
                )
                WikiIconButton(
                    icon = WikiIcons.Delete,
                    contentDescription = stringResource(R.string.seedmap_remove_saved_seed, name),
                    onClick = onRemove,
                    modifier = Modifier.testTag(worldRemoveTag(saved)),
                )
            }
        },
    ) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
