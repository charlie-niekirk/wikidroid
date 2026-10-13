package dev.cniekirk.wikidroid.feature.seedmap

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.McVersion

internal fun dimensionItemTag(dimension: Dimension): String = "seed-map-dimension-${dimension.name}"

internal fun dimensionIconTag(dimension: Dimension): String = "seed-map-dimension-icon-${dimension.name}"

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

/** The ground each dimension's card sits on: grass, netherrack and end stone. */
internal fun Dimension.cardColor(): Color =
    when (this) {
        Dimension.OVERWORLD -> GrassColor
        Dimension.NETHER -> NetherrackColor
        Dimension.END -> EndStoneColor
    }

private val GrassColor = Color(0xFF6A9E3F)
private val NetherrackColor = Color(0xFF7A2E2B)
private val EndStoneColor = Color(0xFFD9D79E)

/** The oldest version that has [this] dimension, for the "available from" line. */
internal fun Dimension.firstVersion(): McVersion = McVersion.entries.first { it.supports(this) }

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
