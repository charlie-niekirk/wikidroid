package dev.cniekirk.wikidroid.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A seed the player saved for the seed map. [version] is the Minecraft version's display name (for example
 * "1.21.4") rather than an enum value, so a saved seed survives the list of supported versions changing:
 * a version the app no longer knows is simply not selectable. A seed is identified by [seed] and
 * [version] together, since the same number is a different world in each version.
 */
@Immutable
@Serializable
data class SavedSeed(
    val seed: Long,
    val version: String,
    val label: String? = null,
)
