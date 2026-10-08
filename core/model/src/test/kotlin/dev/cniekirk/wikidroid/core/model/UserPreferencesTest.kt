package dev.cniekirk.wikidroid.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class UserPreferencesTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun roundTrip_preservesEveryField() {
        val prefs =
            UserPreferences(
                themeMode = ThemeMode.Dark,
                dynamicColor = false,
                textScale = 1.25f,
                preferredEdition = Edition.Bedrock,
                saveHistory = false,
            )

        val decoded = json.decodeFromString<UserPreferences>(json.encodeToString(prefs))

        assertThat(decoded).isEqualTo(prefs)
    }

    @Test
    fun decode_emptyObject_usesDefaults() {
        assertThat(json.decodeFromString<UserPreferences>("{}")).isEqualTo(UserPreferences())
    }

    @Test
    fun decode_missingFieldsFromOlderFile_fallBackToDefaults() {
        val decoded = json.decodeFromString<UserPreferences>("""{"themeMode":"Light"}""")

        assertThat(decoded).isEqualTo(UserPreferences(themeMode = ThemeMode.Light))
    }
}
