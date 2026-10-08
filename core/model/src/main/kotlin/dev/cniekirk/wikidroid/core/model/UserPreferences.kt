package dev.cniekirk.wikidroid.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
enum class Edition { Java, Bedrock }

/** Persisted as JSON, so every field needs a default to keep older files readable. */
@Immutable
@Serializable
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val textScale: Float = DEFAULT_TEXT_SCALE,
    val preferredEdition: Edition = Edition.Java,
    val saveHistory: Boolean = true,
) {
    companion object {
        const val DEFAULT_TEXT_SCALE = 1f
        const val MIN_TEXT_SCALE = 0.85f
        const val MAX_TEXT_SCALE = 1.5f
    }
}
