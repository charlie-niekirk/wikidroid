package dev.cniekirk.wikidroid.feature.seedmap

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.McVersion

internal const val VERSION_SHEET_TAG = "seed-map-version-sheet"
internal const val DIMENSION_SHEET_TAG = "seed-map-dimension-sheet"

internal fun versionItemTag(version: McVersion): String = "seed-map-version-${version.name}"

internal fun dimensionItemTag(dimension: Dimension): String = "seed-map-dimension-${dimension.name}"

internal fun dimensionIconTag(dimension: Dimension): String = "seed-map-dimension-icon-${dimension.name}"

/** Newest first, which is the order a player looking for a current seed wants. */
internal val VersionsNewestFirst: List<McVersion> = McVersion.entries.reversed()

@StringRes
internal fun Dimension.labelRes(): Int =
    when (this) {
        Dimension.OVERWORLD -> R.string.seedmap_dimension_overworld
        Dimension.NETHER -> R.string.seedmap_dimension_nether
        Dimension.END -> R.string.seedmap_dimension_end
    }

/** Grass block, nether wart and ender pearl: wiki sprites like the structure icons. */
@DrawableRes
internal fun Dimension.iconRes(): Int =
    when (this) {
        Dimension.OVERWORLD -> R.drawable.dimension_overworld
        Dimension.NETHER -> R.drawable.dimension_nether
        Dimension.END -> R.drawable.dimension_end
    }

/** The oldest version that has [this] dimension, for the "available from" line. */
private fun Dimension.firstVersion(): McVersion = McVersion.entries.first { it.supports(this) }

@Composable
internal fun DimensionIcon(
    dimension: Dimension,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Image(
        bitmap = ImageBitmap.imageResource(dimension.iconRes()),
        contentDescription = null,
        modifier = modifier.size(size).testTag(dimensionIconTag(dimension)),
        filterQuality = FilterQuality.None,
    )
}

/** Every version, newest first, with the current one ticked. Picking one applies it; the screen closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VersionSheet(
    selected: McVersion,
    onSelect: (McVersion) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column {
            SheetTitle(R.string.seedmap_version_sheet_title)
            LazyColumn(modifier = Modifier.testTag(VERSION_SHEET_TAG)) {
                items(VersionsNewestFirst, key = { it.name }) { version ->
                    ListItem(
                        selected = version == selected,
                        onClick = { onSelect(version) },
                        modifier = Modifier.testTag(versionItemTag(version)),
                        trailingContent =
                            if (version == selected) {
                                { WikiIcon(icon = WikiIcons.Check, contentDescription = null) }
                            } else {
                                null
                            },
                    ) {
                        Text(version.label)
                    }
                }
            }
        }
    }
}

/**
 * The three dimensions with their icons. Those the selected [version] does not have are listed but
 * disabled, with the version they appear in, so the player can see why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DimensionSheet(
    version: McVersion,
    selected: Dimension,
    onSelect: (Dimension) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.padding(bottom = 24.dp).testTag(DIMENSION_SHEET_TAG)) {
            SheetTitle(R.string.seedmap_dimension_sheet_title)
            Dimension.entries.forEach { dimension ->
                val supported = version.supports(dimension)
                ListItem(
                    selected = dimension == selected,
                    onClick = { onSelect(dimension) },
                    enabled = supported,
                    modifier = Modifier.testTag(dimensionItemTag(dimension)),
                    leadingContent = { DimensionIcon(dimension, size = 32.dp) },
                    supportingContent =
                        if (supported) {
                            null
                        } else {
                            { Text(stringResource(R.string.seedmap_dimension_from, dimension.firstVersion().label)) }
                        },
                    trailingContent =
                        if (dimension == selected) {
                            { WikiIcon(icon = WikiIcons.Check, contentDescription = null) }
                        } else {
                            null
                        },
                ) {
                    Text(stringResource(dimension.labelRes()))
                }
            }
        }
    }
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
