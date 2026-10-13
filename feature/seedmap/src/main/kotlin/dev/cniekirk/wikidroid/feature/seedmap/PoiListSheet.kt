package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.model.PointOfInterest
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.hypot
import kotlin.math.roundToLong

internal const val POI_LIST_TAG = "seed-map-poi-list"
internal const val POI_LIST_EMPTY_TAG = "seed-map-poi-list-empty"

internal fun poiRowTag(point: PointOfInterest): String = "seed-map-poi-${point.id}"

/**
 * The player's points in this seed and dimension, by name, each with how far it is from the middle of the map.
 * Tapping one closes the sheet and takes the map there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PoiListSheet(
    pois: ImmutableList<PointOfInterest>,
    from: BlockPos,
    onGoTo: (PointOfInterest) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        SheetTitle(R.string.seedmap_pois_title)
        if (pois.isEmpty()) {
            Text(
                text = stringResource(R.string.seedmap_pois_empty),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 24.dp,
                            vertical = 16.dp,
                        ).testTag(POI_LIST_EMPTY_TAG),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val sorted = pois.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
        LazyColumn(modifier = Modifier.padding(bottom = 16.dp).testTag(POI_LIST_TAG)) {
            items(sorted, key = { it.id }) { point ->
                ListItem(
                    onClick = { onGoTo(point) },
                    modifier = Modifier.padding(horizontal = 8.dp).testTag(poiRowTag(point)),
                    leadingContent = { PoiMarker(point.category.icon, point.color) },
                    supportingContent = {
                        Text(
                            stringResource(R.string.seedmap_coordinates, point.x, point.z) + " · " +
                                stringResource(R.string.seedmap_poi_distance, "%,d".format(distance(from, point))),
                        )
                    },
                ) {
                    Text(point.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Blocks from [from] to [point] in a straight line, ignoring height. */
internal fun distance(
    from: BlockPos,
    point: PointOfInterest,
): Long = hypot((point.x - from.x).toDouble(), (point.z - from.z).toDouble()).roundToLong()
