package dev.cniekirk.wikidroid.feature.seedmap

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.cniekirk.wikidroid.core.navigation.ArticleKey
import dev.cniekirk.wikidroid.core.navigation.EntryProviderInstaller
import dev.cniekirk.wikidroid.core.navigation.Navigator
import dev.cniekirk.wikidroid.core.navigation.SeedMapKey
import dev.cniekirk.wikidroid.core.seedmap.TileCache
import dev.cniekirk.wikidroid.core.ui.pane.WikiPanes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Registers the Seed map tab. An article opened from the map sits beside it on wide windows. */
@Inject
@ContributesIntoSet(AppScope::class)
class SeedMapEntryInstaller(
    private val tiles: TileCache,
) : EntryProviderInstaller {
    override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
        entry<SeedMapKey>(metadata = WikiPanes.list()) {
            SeedMapRoute(tiles = tiles, onOpenArticle = { navigator.navigate(ArticleKey(it)) })
        }
    }
}
