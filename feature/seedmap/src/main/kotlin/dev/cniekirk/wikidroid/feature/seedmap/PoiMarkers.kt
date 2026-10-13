package dev.cniekirk.wikidroid.feature.seedmap

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.model.PoiCategory
import dev.cniekirk.wikidroid.core.model.PoiColor
import dev.cniekirk.wikidroid.core.model.PointOfInterest
import kotlinx.collections.immutable.ImmutableList

/** The colour a [PoiColor] is drawn in: saturated enough to read on any biome, with white on top. */
internal val PoiColor.color: Color
    get() =
        when (this) {
            PoiColor.RED -> Color(0xFFE53935)
            PoiColor.ORANGE -> Color(0xFFFB8C00)
            PoiColor.YELLOW -> Color(0xFFF9A825)
            PoiColor.GREEN -> Color(0xFF43A047)
            PoiColor.CYAN -> Color(0xFF00ACC1)
            PoiColor.BLUE -> Color(0xFF1E88E5)
            PoiColor.PURPLE -> Color(0xFF8E24AA)
            PoiColor.PINK -> Color(0xFFD81B60)
        }

@get:DrawableRes
internal val PoiCategory.icon: Int
    get() =
        when (this) {
            PoiCategory.BASE -> WikiIcons.Home
            PoiCategory.PORTAL -> WikiIcons.Portal
            PoiCategory.FARM -> WikiIcons.Eco
            PoiCategory.MINE -> WikiIcons.Diamond
            PoiCategory.LANDMARK -> WikiIcons.Landscape
            PoiCategory.OTHER -> WikiIcons.LocationOn
        }

@get:StringRes
internal val PoiCategory.label: Int
    get() =
        when (this) {
            PoiCategory.BASE -> R.string.seedmap_poi_base
            PoiCategory.PORTAL -> R.string.seedmap_poi_portal
            PoiCategory.FARM -> R.string.seedmap_poi_farm
            PoiCategory.MINE -> R.string.seedmap_poi_mine
            PoiCategory.LANDMARK -> R.string.seedmap_poi_landmark
            PoiCategory.OTHER -> R.string.seedmap_poi_other
        }

@get:StringRes
internal val PoiColor.label: Int
    get() =
        when (this) {
            PoiColor.RED -> R.string.seedmap_poi_color_red
            PoiColor.ORANGE -> R.string.seedmap_poi_color_orange
            PoiColor.YELLOW -> R.string.seedmap_poi_color_yellow
            PoiColor.GREEN -> R.string.seedmap_poi_color_green
            PoiColor.CYAN -> R.string.seedmap_poi_color_cyan
            PoiColor.BLUE -> R.string.seedmap_poi_color_blue
            PoiColor.PURPLE -> R.string.seedmap_poi_color_purple
            PoiColor.PINK -> R.string.seedmap_poi_color_pink
        }

/** The map's marker for a point, for sheets and lists: the same colour, outline and glyph ([icon], if any). */
@Composable
internal fun PoiMarker(
    @DrawableRes icon: Int?,
    color: PoiColor,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(color.color, CircleShape)
                .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            WikiIcon(
                icon = icon,
                contentDescription = null,
                modifier = Modifier.size(size * GLYPH_SCALE),
                tint = Color.White,
            )
        }
    }
}

/** The point's name, or its category's when it has none. */
@Composable
internal fun PointOfInterest.displayName(): String = name ?: stringResource(category.label)

/**
 * What the canvas needs to draw the player's points: a glyph per category, and each point's name measured once
 * (whenever the points change) rather than every frame.
 */
@Immutable
internal class PoiPaint(
    private val glyphs: Map<PoiCategory, Painter>,
    private val labels: Map<Long, TextLayoutResult>,
) {
    fun glyph(category: PoiCategory): Painter? = glyphs[category]

    fun label(point: PointOfInterest): TextLayoutResult? = labels[point.id]

    /** How far a label can reach sideways from its point, for culling. */
    val labelReach: Float = labels.values.maxOfOrNull { it.size.width / 2f } ?: 0f
}

@Composable
internal fun rememberPoiPaint(pois: ImmutableList<PointOfInterest>): PoiPaint {
    val glyphs = PoiCategory.entries.associateWith { painterResource(it.icon) }
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val names = pois.associate { it.id to it.displayName() }
    return remember(glyphs, names) {
        val labels =
            names.mapValues { (_, name) ->
                // Unbounded, so a point near or past the edge never gets a negative width to lay out in.
                measurer.measure(
                    text = name,
                    style = LabelStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = MAX_LABEL_WIDTH_PX),
                )
            }
        PoiPaint(glyphs, labels)
    }
}

private val LabelStyle =
    TextStyle(
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        shadow = Shadow(Color.Black, Offset(0f, 1f), blurRadius = 3f),
    )

private const val MAX_LABEL_WIDTH_PX = 600
private const val GLYPH_SCALE = 0.6f

internal val PoiMarkerRadius = 13.dp
private val PoiGlyphSize = 16.dp

/** Names show once the map is zoomed in this far, so a wide view is not a wall of text. */
internal const val POI_LABEL_MAX_BLOCKS_PER_PIXEL = 4f

/** A round marker in the point's colour with its category's glyph, and its name under it when zoomed in. */
internal fun DrawScope.drawPoi(
    paint: PoiPaint,
    point: PointOfInterest,
    center: Offset,
    showLabel: Boolean,
) {
    val radius = PoiMarkerRadius.toPx()
    drawCircle(Color.Black.copy(alpha = 0.35f), radius + 1.5.dp.toPx(), center + Offset(0f, 1.dp.toPx()))
    drawCircle(point.color.color, radius, center)
    drawCircle(Color.White, radius, center, style = Stroke(2.dp.toPx()))
    paint.glyph(point.category)?.let { glyph ->
        val glyphSize = PoiGlyphSize.toPx()
        translate(center.x - glyphSize / 2, center.y - glyphSize / 2) {
            with(glyph) { draw(Size(glyphSize, glyphSize), colorFilter = ColorFilter.tint(Color.White)) }
        }
    }
    if (showLabel) {
        paint.label(point)?.let { label ->
            drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y + radius + 2.dp.toPx()))
        }
    }
}
