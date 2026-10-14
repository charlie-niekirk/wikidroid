package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIconButton
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.seedmap.Dimension

internal const val MEASURE_BAR_TAG = "seed-map-measure-bar"
internal const val MEASURE_TEXT_TAG = "seed-map-measure-text"
internal const val MEASURE_CLOSE_TAG = "seed-map-measure-close"

/** Under the chips while the ruler is on: what to tap next, or the distance and its other-dimension length. */
@Composable
internal fun MeasureBar(
    measure: Measurement,
    dimension: Dimension,
    onAction: (SeedMapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag(MEASURE_BAR_TAG),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WikiIcon(icon = WikiIcons.Straighten, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = measureText(measure, dimension),
                modifier = Modifier.testTag(MEASURE_TEXT_TAG),
                style = MaterialTheme.typography.labelLarge,
            )
            WikiIconButton(
                icon = WikiIcons.Close,
                contentDescription = stringResource(R.string.seedmap_measure_stop),
                onClick = { onAction(SeedMapAction.StopMeasure) },
                modifier = Modifier.testTag(MEASURE_CLOSE_TAG),
            )
        }
    }
}

@Composable
private fun measureText(
    measure: Measurement,
    dimension: Dimension,
): String {
    val blocks = measure.blocks
    return when {
        measure.start == null -> {
            stringResource(R.string.seedmap_measure_pick_start)
        }

        blocks == null -> {
            stringResource(R.string.seedmap_measure_pick_end)
        }

        else -> {
            val distance = "%,d".format(blocks)
            when (val other = dimension.travelEquivalent(blocks)) {
                null -> {
                    stringResource(R.string.seedmap_measure_blocks, distance)
                }

                else -> {
                    val otherText = "%,d".format(other.second)
                    if (other.first == Dimension.NETHER) {
                        stringResource(R.string.seedmap_measure_through_nether, distance, otherText)
                    } else {
                        stringResource(R.string.seedmap_measure_in_overworld, distance, otherText)
                    }
                }
            }
        }
    }
}
