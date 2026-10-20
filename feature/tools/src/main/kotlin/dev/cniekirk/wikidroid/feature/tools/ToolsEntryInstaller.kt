package dev.cniekirk.wikidroid.feature.tools

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.cniekirk.wikidroid.core.navigation.EntryProviderInstaller
import dev.cniekirk.wikidroid.core.navigation.Navigator
import dev.cniekirk.wikidroid.core.navigation.ToolsKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Registers the Tools tab and its tools. None of them lists articles, so none is a pane. */
@Inject
@ContributesIntoSet(AppScope::class)
class ToolsEntryInstaller : EntryProviderInstaller {
    override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
        entry<ToolsKey> {
            ToolsScreen(onOpenTool = { navigator.navigate(it.key) })
        }
    }
}
