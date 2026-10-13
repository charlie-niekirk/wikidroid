package dev.cniekirk.wikidroid.feature.seedmap

import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** What a row of the expanded seed search does when tapped. */
internal sealed interface SeedSuggestion {
    /** The text read as coordinates: move the map there. */
    data class GoTo(
        val pos: BlockPos,
    ) : SeedSuggestion

    /** The text as a seed, parsed like the game does. */
    data class UseSeed(
        val text: String,
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
 * A position comes first, then the seed itself, the random seed, and the saved seeds whose name or number
 * contains the query.
 */
internal fun seedSuggestions(
    query: String,
    saved: List<SavedSeed>,
): ImmutableList<SeedSuggestion> {
    val text = query.trim()
    val position = parseCoordinates(text)
    return buildList {
        when {
            position != null -> add(SeedSuggestion.GoTo(position))
            text.isNotEmpty() -> add(SeedSuggestion.UseSeed(text))
        }
        add(SeedSuggestion.Random)
        saved.filter { it.matches(text) }.forEach { add(SeedSuggestion.Saved(it)) }
    }.toImmutableList()
}

private fun SavedSeed.matches(text: String): Boolean =
    text.isEmpty() || displayName().contains(text, ignoreCase = true) || text in seed.toString()
