package dev.cniekirk.wikidroid.core.seedmap

import kotlin.random.Random

/**
 * Turns what a player types into a world seed the way the game's world-creation screen does: text that is
 * a number (a 64-bit signed integer) is the seed, any other text is hashed with Java's `String.hashCode()`,
 * and nothing at all picks a random seed.
 */
object SeedParser {
    /** The seed for [text]. [random] is only consulted when [text] is blank. */
    fun parse(
        text: String,
        random: Random = Random.Default,
    ): Long {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return random.nextLong()
        return trimmed.toLongOrNull() ?: trimmed.hashCode().toLong()
    }
}
