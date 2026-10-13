package dev.cniekirk.wikidroid.feature.seedmap

import dev.cniekirk.wikidroid.core.model.PointOfInterest
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.seedmap.StructureWikiTitles
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** What a row of the expanded seed search does when tapped. */
internal sealed interface SeedSuggestion {
    /** The text read as coordinates: move the map there. */
    data class GoTo(
        val pos: BlockPos,
    ) : SeedSuggestion

    /** A structure whose name matches the text: find the closest one to the middle of the map. */
    data class Nearest(
        val type: StructureType,
    ) : SeedSuggestion

    /** The text as a seed, parsed like the game does. */
    data class UseSeed(
        val text: String,
    ) : SeedSuggestion

    /** One of the player's points whose name matches the text: go to it. */
    data class Poi(
        val point: PointOfInterest,
    ) : SeedSuggestion

    data object Random : SeedSuggestion

    data class Saved(
        val saved: SavedSeed,
    ) : SeedSuggestion
}

private val Coordinates =
    Regex(
        """^\s*(?:x\s*[:=]?\s*)?(-?\d+)\s*[,\s]\s*(?:(?:y\s*[:=]?\s*)?-?\d+\s*[,\s]\s*)?(?:z\s*[:=]?\s*)?(-?\d+)\s*$""",
        RegexOption.IGNORE_CASE,
    )

/**
 * [text] as a block position: two whole numbers (`120 -340`, `120, -340`, `x120 z-340`), or three as the game's
 * F3 screen and `/tp` write them (`120 64 -340`, the height is dropped). Pulled inside the world border. A single
 * number is a seed, never a position, so this returns null for it.
 */
internal fun parseCoordinates(text: String): BlockPos? {
    val (x, z) = Coordinates.matchEntire(text)?.destructured ?: return null
    val blockX = x.toCoordinate()
    val blockZ = z.toCoordinate()
    return if (blockX != null && blockZ != null) BlockPos(blockX, blockZ) else null
}

private fun String.toCoordinate(): Int? {
    val limit = SeedMapEngine.MAX_COORDINATE.toLong()
    return toLongOrNull()?.coerceIn(-limit, limit)?.toInt()
}

/**
 * The rows under the expanded seed search for [query], which is empty until the player types something new.
 * A position comes first, then the structures of [world] whose name contains the query, the player's [pois] whose
 * name does, the seed itself, the random seed, and the saved seeds whose name or number contains the query.
 */
internal fun seedSuggestions(
    query: String,
    saved: List<SavedSeed>,
    world: MapWorld,
    pois: List<PointOfInterest> = emptyList(),
): ImmutableList<SeedSuggestion> {
    val text = query.trim()
    val position = parseCoordinates(text)
    return buildList {
        when {
            position != null -> {
                add(SeedSuggestion.GoTo(position))
            }

            text.isNotEmpty() -> {
                structuresMatching(text, world).forEach { add(SeedSuggestion.Nearest(it)) }
                poisMatching(text, pois).forEach { add(SeedSuggestion.Poi(it)) }
                add(SeedSuggestion.UseSeed(text))
            }
        }
        add(SeedSuggestion.Random)
        saved.filter { it.matches(text) }.forEach { add(SeedSuggestion.Saved(it)) }
    }.toImmutableList()
}

/**
 * The structures [world] can have whose wiki name contains [text], ignoring case. A single letter matches too
 * much to be useful, so it matches nothing. The two ruined portals share a name; only the one in [world] is here.
 */
internal fun structuresMatching(
    text: String,
    world: MapWorld,
): List<StructureType> =
    if (text.length < MIN_STRUCTURE_QUERY) {
        emptyList()
    } else {
        StructureType.availableIn(world).filter { StructureWikiTitles.titleFor(it).contains(text, ignoreCase = true) }
    }

/** The points whose name contains [text], ignoring case; like structures, a single letter matches nothing. */
internal fun poisMatching(
    text: String,
    pois: List<PointOfInterest>,
): List<PointOfInterest> =
    if (text.length < MIN_STRUCTURE_QUERY) {
        emptyList()
    } else {
        pois.filter { it.name?.contains(text, ignoreCase = true) == true }
    }

/** Whether the search key should run [this]: a row that acts on the typed text, not a fixed one. */
internal val SeedSuggestion.isForTypedText: Boolean
    get() =
        this is SeedSuggestion.GoTo ||
            this is SeedSuggestion.Nearest ||
            this is SeedSuggestion.Poi ||
            this is SeedSuggestion.UseSeed

private const val MIN_STRUCTURE_QUERY = 2

private fun SavedSeed.matches(text: String): Boolean =
    text.isEmpty() || displayName().contains(text, ignoreCase = true) || text in seed.toString()
