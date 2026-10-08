package dev.cniekirk.wikidroid.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.cniekirk.wikidroid.core.navigation.AboutKey
import dev.cniekirk.wikidroid.core.navigation.EntryProviderInstaller
import dev.cniekirk.wikidroid.core.navigation.Navigator
import dev.cniekirk.wikidroid.core.navigation.SettingsKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Registers the Settings tab and the About screen above it. Neither opens articles, so neither is a pane. */
@Inject
@ContributesIntoSet(AppScope::class)
class SettingsEntryInstaller : EntryProviderInstaller {
    override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
        entry<SettingsKey> {
            SettingsRoute(onOpenAbout = { navigator.navigate(AboutKey) })
        }
        entry<AboutKey> {
            AboutRoute(onNavigateBack = { navigator.goBack() })
        }
    }
}
