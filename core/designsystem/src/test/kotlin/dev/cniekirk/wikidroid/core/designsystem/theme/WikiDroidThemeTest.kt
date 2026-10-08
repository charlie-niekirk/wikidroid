package dev.cniekirk.wikidroid.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class WikiDroidThemeTest : ComposeTest() {
    private fun capturedScheme(
        themeMode: ThemeMode,
        dynamicColor: Boolean,
    ): ColorScheme {
        lateinit var scheme: ColorScheme
        composeRule.setContent {
            WikiDroidTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                Capture { scheme = it }
            }
        }
        composeRule.waitForIdle()
        return scheme
    }

    @Test
    fun light_usesTheFallbackLightScheme() {
        assertThat(capturedScheme(ThemeMode.Light, dynamicColor = false)).isEqualTo(LightColorScheme)
    }

    @Test
    fun dark_usesTheFallbackDarkScheme() {
        assertThat(capturedScheme(ThemeMode.Dark, dynamicColor = false)).isEqualTo(DarkColorScheme)
    }

    @Test
    fun system_followsTheDeviceNightMode() {
        RuntimeEnvironment.setQualifiers("+night")

        assertThat(capturedScheme(ThemeMode.System, dynamicColor = false)).isEqualTo(DarkColorScheme)
    }

    @Test
    fun system_isLightByDefault() {
        assertThat(capturedScheme(ThemeMode.System, dynamicColor = false)).isEqualTo(LightColorScheme)
    }

    @Test
    fun dynamicColor_rendersWithAFrameworkDerivedScheme() {
        val scheme = capturedScheme(ThemeMode.Light, dynamicColor = true)

        assertThat(scheme.primary).isNotEqualTo(LightColorScheme.primary)
    }

    @Test
    @Config(sdk = [29])
    fun dynamicColor_fallsBackBeforeAndroid12() {
        assertThat(capturedScheme(ThemeMode.Light, dynamicColor = true)).isEqualTo(LightColorScheme)
    }

    @Test
    fun typographyScaled_multipliesSizeAndLineHeight() {
        val base = Typography()

        val scaled = base.scaled(1.5f)

        assertThat(scaled.bodyLarge.fontSize).isEqualTo(base.bodyLarge.fontSize * 1.5f)
        assertThat(scaled.bodyLarge.lineHeight).isEqualTo(base.bodyLarge.lineHeight * 1.5f)
        assertThat(scaled.labelSmall.fontSize.value).isWithin(0.001f).of(11f * 1.5f)
        assertThat(base.scaled(1f)).isSameInstanceAs(base)
        assertThat(WikiDroidTypography.bodyLarge.lineHeight).isEqualTo(26.sp)
    }
}

@Composable
private fun Capture(onScheme: (ColorScheme) -> Unit) {
    onScheme(MaterialTheme.colorScheme)
}
