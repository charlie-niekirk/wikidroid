package dev.cniekirk.wikidroid.feature.tools

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.navigation.ResourceCalculatorKey
import dev.cniekirk.wikidroid.core.navigation.WikiKey

/** The tools listed on the Tools tab, in order. Each opens its own screen at [key]. */
enum class Tool(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @DrawableRes val icon: Int,
    val key: WikiKey,
) {
    ResourceCalculator(
        title = R.string.tools_resource_calculator,
        summary = R.string.tools_resource_calculator_summary,
        icon = WikiIcons.Calculate,
        key = ResourceCalculatorKey,
    ),
}
