package dev.cniekirk.wikidroid.core.model

import androidx.compose.runtime.Immutable

/**
 * A place the player marked on the seed map: a base, a portal, a farm. It belongs to a [seed] and a [dimension]
 * but not to a version, since a base stays where it is when the game updates. [dimension] is the seed map's
 * dimension name (`OVERWORLD`, `NETHER`, `END`), a string for the same reason [SavedSeed.version] is one: this
 * module does not know the seed map's types.
 *
 * [id] is 0 until the point is saved. [name] is null when the player gave none; the map then shows the
 * category's name. [y] is optional because the map is flat.
 */
@Immutable
data class PointOfInterest(
    val id: Long = 0,
    val seed: Long,
    val dimension: String,
    val x: Int,
    val z: Int,
    val y: Int? = null,
    val name: String? = null,
    val category: PoiCategory = PoiCategory.OTHER,
    val color: PoiColor = category.defaultColor,
    val note: String? = null,
    val createdAt: Long = 0,
)

/** What kind of place a point is, which picks its icon. */
enum class PoiCategory(
    val defaultColor: PoiColor,
) {
    BASE(PoiColor.BLUE),
    PORTAL(PoiColor.PURPLE),
    FARM(PoiColor.GREEN),
    MINE(PoiColor.CYAN),
    LANDMARK(PoiColor.ORANGE),
    OTHER(PoiColor.RED),
}

/** The marker colours a player can pick from. The colour values live with the map that draws them. */
enum class PoiColor { RED, ORANGE, YELLOW, GREEN, CYAN, BLUE, PURPLE, PINK }
