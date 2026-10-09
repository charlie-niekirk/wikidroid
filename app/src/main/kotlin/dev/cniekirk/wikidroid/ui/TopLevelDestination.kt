package dev.cniekirk.wikidroid.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.cniekirk.wikidroid.R
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.navigation.ExploreKey
import dev.cniekirk.wikidroid.core.navigation.LibraryKey
import dev.cniekirk.wikidroid.core.navigation.SearchKey
import dev.cniekirk.wikidroid.core.navigation.SeedMapKey
import dev.cniekirk.wikidroid.core.navigation.SettingsKey
import dev.cniekirk.wikidroid.core.navigation.TopLevelKey

/** How a bottom-bar tab looks. The tabs themselves (and their order) are `TopLevelKeys`. */
internal val TopLevelKey.iconRes: Int
    @DrawableRes get() =
        when (this) {
            ExploreKey -> WikiIcons.Explore
            SearchKey -> WikiIcons.Search
            SeedMapKey -> WikiIcons.Map
            LibraryKey -> WikiIcons.Bookmark
            SettingsKey -> WikiIcons.Settings
        }

internal val TopLevelKey.labelRes: Int
    @StringRes get() =
        when (this) {
            ExploreKey -> R.string.tab_explore
            SearchKey -> R.string.tab_search
            SeedMapKey -> R.string.tab_seed_map
            LibraryKey -> R.string.tab_library
            SettingsKey -> R.string.tab_settings
        }
