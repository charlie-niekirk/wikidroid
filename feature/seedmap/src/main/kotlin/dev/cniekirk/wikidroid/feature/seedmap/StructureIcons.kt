package dev.cniekirk.wikidroid.feature.seedmap

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import kotlin.math.roundToInt

/**
 * A minecraft.wiki inventory sprite (see `feature/seedmap/NOTICE.md`) for a structure, stored in
 * `drawable-nodpi` so it decodes at its own size and can be drawn without filtering.
 */
@JvmInline
internal value class StructureIcon(
    @DrawableRes val resId: Int,
)

/**
 * The sprite for each structure, picked in one place. The `when` has no `else`, so a new [StructureType]
 * does not compile until it gets an icon; a few types share one because the game shows them as one thing.
 */
internal fun StructureType.icon(): StructureIcon =
    StructureIcon(
        when (this) {
            StructureType.DESERT_PYRAMID -> R.drawable.structure_desert_pyramid
            StructureType.JUNGLE_TEMPLE -> R.drawable.structure_jungle_temple
            StructureType.SWAMP_HUT -> R.drawable.structure_swamp_hut
            StructureType.IGLOO -> R.drawable.structure_igloo
            StructureType.VILLAGE -> R.drawable.structure_village
            StructureType.OCEAN_RUIN -> R.drawable.structure_ocean_ruin
            StructureType.SHIPWRECK -> R.drawable.structure_shipwreck
            StructureType.MONUMENT -> R.drawable.structure_monument
            StructureType.MANSION -> R.drawable.structure_mansion
            StructureType.OUTPOST -> R.drawable.structure_outpost
            StructureType.RUINED_PORTAL, StructureType.RUINED_PORTAL_NETHER -> R.drawable.structure_ruined_portal
            StructureType.ANCIENT_CITY -> R.drawable.structure_ancient_city
            StructureType.BURIED_TREASURE -> R.drawable.structure_buried_treasure
            StructureType.FORTRESS -> R.drawable.structure_fortress
            StructureType.BASTION -> R.drawable.structure_bastion
            StructureType.NETHER_FOSSIL -> R.drawable.structure_nether_fossil
            StructureType.END_CITY -> R.drawable.structure_end_city
            StructureType.END_GATEWAY -> R.drawable.structure_end_gateway
            StructureType.TRAIL_RUINS -> R.drawable.structure_trail_ruins
            StructureType.TRIAL_CHAMBERS -> R.drawable.structure_trial_chambers
            StructureType.ABANDONED_CAMP -> R.drawable.structure_abandoned_camp
            StructureType.STRONGHOLD -> R.drawable.structure_stronghold
        },
    )

internal val SpawnIcon = StructureIcon(R.drawable.structure_spawn)

/** The icon for what a tap picked; bare biomes have none. */
internal fun MapSelection.icon(): StructureIcon? =
    when (this) {
        is MapSelection.Structure -> structure.type.icon()
        is MapSelection.Spawn -> SpawnIcon
        is MapSelection.Biome -> null
    }

internal fun structureIconTag(icon: StructureIcon): String = "seed-map-icon-${icon.resId}"

/** Every icon the map can draw, for decoding them once. */
internal val AllStructureIcons: List<StructureIcon> =
    (StructureType.entries.map { it.icon() } + SpawnIcon).distinct()

/**
 * The sprites decoded once, for the canvas to draw every frame. A sprite that fails to decode is simply
 * absent, and the canvas falls back to the coloured marker for it.
 */
internal class StructureSprites(
    private val bitmaps: Map<StructureIcon, ImageBitmap>,
) {
    operator fun get(icon: StructureIcon): ImageBitmap? = bitmaps[icon]

    companion object {
        val Empty = StructureSprites(emptyMap())

        fun load(resources: Resources): StructureSprites =
            StructureSprites(
                buildMap {
                    AllStructureIcons.forEach { icon ->
                        runCatching { ImageBitmap.imageResource(resources, icon.resId) }.getOrNull()?.let {
                            put(icon, it)
                        }
                    }
                },
            )
    }
}

/** The sprites are 16 or 32 px, so a pin is drawn at a whole multiple of 32 px near this size. */
private val PinSpriteSize = 30.dp
private const val SPRITE_GRID_PX = 32

/** The size a sprite is drawn at, in pixels: a multiple of 32 so both 16 and 32 px art scale by a whole number. */
internal fun pinSpritePx(densityPx: Float): Int =
    ((PinSpriteSize.value * densityPx / SPRITE_GRID_PX).roundToInt().coerceAtLeast(1)) * SPRITE_GRID_PX

/** How far a pin reaches from its centre, in pixels. */
internal fun pinReachPx(densityPx: Float): Float = pinSpritePx(densityPx) / 2f

/** Draws [sprite] centred on [center], its pixels kept sharp. */
internal fun DrawScope.drawSprite(
    sprite: ImageBitmap,
    center: Offset,
) {
    val size = pinSpritePx(density)
    // A whole number of pixels per sprite pixel, whatever the sprite's own size.
    drawImage(
        image = sprite,
        srcSize = IntSize(sprite.width, sprite.height),
        dstOffset = IntOffset((center.x - size / 2f).roundToInt(), (center.y - size / 2f).roundToInt()),
        dstSize = IntSize(size, size),
        filterQuality = FilterQuality.None,
    )
}

/** The icon as an [Image] for chips, rows and sheets; the same pixels the map draws, never smoothed. */
@Composable
internal fun StructureIconImage(
    icon: StructureIcon,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Image(
        bitmap = ImageBitmap.imageResource(icon.resId),
        contentDescription = null,
        modifier = modifier.size(size).testTag(structureIconTag(icon)),
        filterQuality = FilterQuality.None,
    )
}
