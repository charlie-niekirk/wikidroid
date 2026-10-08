package dev.cniekirk.wikidroid.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Material 3 defaults with heavier titles and slightly looser body lines, which suit long-form reading. */
internal val WikiDroidTypography: Typography =
    Typography().let { base ->
        base.copy(
            headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(lineHeight = BODY_LARGE_LINE_HEIGHT_SP.sp),
            bodyMedium = base.bodyMedium.copy(lineHeight = BODY_MEDIUM_LINE_HEIGHT_SP.sp),
        )
    }

private const val BODY_LARGE_LINE_HEIGHT_SP = 26
private const val BODY_MEDIUM_LINE_HEIGHT_SP = 22

/** Scales every style's size and line height, for the user's text-size setting. */
fun Typography.scaled(factor: Float): Typography =
    if (factor == 1f) {
        this
    } else {
        Typography(
            displayLarge = displayLarge.scaled(factor),
            displayMedium = displayMedium.scaled(factor),
            displaySmall = displaySmall.scaled(factor),
            headlineLarge = headlineLarge.scaled(factor),
            headlineMedium = headlineMedium.scaled(factor),
            headlineSmall = headlineSmall.scaled(factor),
            titleLarge = titleLarge.scaled(factor),
            titleMedium = titleMedium.scaled(factor),
            titleSmall = titleSmall.scaled(factor),
            bodyLarge = bodyLarge.scaled(factor),
            bodyMedium = bodyMedium.scaled(factor),
            bodySmall = bodySmall.scaled(factor),
            labelLarge = labelLarge.scaled(factor),
            labelMedium = labelMedium.scaled(factor),
            labelSmall = labelSmall.scaled(factor),
        )
    }

private fun TextStyle.scaled(factor: Float): TextStyle =
    copy(
        fontSize = fontSize * factor,
        lineHeight =
            lineHeight * factor,
    )
