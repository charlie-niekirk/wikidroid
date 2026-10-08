package dev.cniekirk.wikidroid.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Minecraft-inspired fallback palette, used when dynamic colour is off or unavailable:
 * grass green (primary), stone (secondary) and diamond (tertiary).
 */
internal val LightColorScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF3F6B2A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFBFF2A2),
        onPrimaryContainer = Color(0xFF0A2000),
        inversePrimary = Color(0xFFA3D68A),
        secondary = Color(0xFF56624C),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD9E7CA),
        onSecondaryContainer = Color(0xFF141F0D),
        tertiary = Color(0xFF006A6A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF6FF7F6),
        onTertiaryContainer = Color(0xFF002020),
        background = Color(0xFFFDFDF5),
        onBackground = Color(0xFF1A1C18),
        surface = Color(0xFFFDFDF5),
        onSurface = Color(0xFF1A1C18),
        surfaceVariant = Color(0xFFE0E4D6),
        onSurfaceVariant = Color(0xFF43483E),
        surfaceTint = Color(0xFF3F6B2A),
        inverseSurface = Color(0xFF2F312D),
        inverseOnSurface = Color(0xFFF1F1EA),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF74796D),
        outlineVariant = Color(0xFFC3C8BB),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFDFDF5),
        surfaceDim = Color(0xFFDADBD2),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F5EC),
        surfaceContainer = Color(0xFFEEEFE6),
        surfaceContainerHigh = Color(0xFFE8E9E0),
        surfaceContainerHighest = Color(0xFFE2E3DA),
    )

internal val DarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFA3D68A),
        onPrimary = Color(0xFF123800),
        primaryContainer = Color(0xFF265111),
        onPrimaryContainer = Color(0xFFBFF2A2),
        inversePrimary = Color(0xFF3F6B2A),
        secondary = Color(0xFFBDCBAF),
        onSecondary = Color(0xFF29341F),
        secondaryContainer = Color(0xFF3F4B34),
        onSecondaryContainer = Color(0xFFD9E7CA),
        tertiary = Color(0xFF4CDADA),
        onTertiary = Color(0xFF003737),
        tertiaryContainer = Color(0xFF004F4F),
        onTertiaryContainer = Color(0xFF6FF7F6),
        background = Color(0xFF1A1C18),
        onBackground = Color(0xFFE2E3DA),
        surface = Color(0xFF1A1C18),
        onSurface = Color(0xFFE2E3DA),
        surfaceVariant = Color(0xFF43483E),
        onSurfaceVariant = Color(0xFFC3C8BB),
        surfaceTint = Color(0xFFA3D68A),
        inverseSurface = Color(0xFFE2E3DA),
        inverseOnSurface = Color(0xFF2F312D),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8D9286),
        outlineVariant = Color(0xFF43483E),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF383A34),
        surfaceDim = Color(0xFF12140F),
        surfaceContainerLowest = Color(0xFF0D0F0B),
        surfaceContainerLow = Color(0xFF1E201B),
        surfaceContainer = Color(0xFF222420),
        surfaceContainerHigh = Color(0xFF2D2F2A),
        surfaceContainerHighest = Color(0xFF383A34),
    )
