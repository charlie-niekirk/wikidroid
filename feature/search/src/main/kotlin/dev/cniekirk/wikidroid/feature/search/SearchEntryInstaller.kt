package dev.cniekirk.wikidroid.feature.search

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.cniekirk.wikidroid.core.navigation.ArticleKey
import dev.cniekirk.wikidroid.core.navigation.EntryProviderInstaller
import dev.cniekirk.wikidroid.core.navigation.Navigator
import dev.cniekirk.wikidroid.core.navigation.SearchKey
import dev.cniekirk.wikidroid.core.ui.pane.WikiPanes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Registers the Search tab. */
@Inject
@ContributesIntoSet(AppScope::class)
class SearchEntryInstaller : EntryProviderInstaller {
    override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
        entry<SearchKey>(metadata = WikiPanes.list()) {
            SearchRoute(onOpenArticle = { navigator.navigate(ArticleKey(it)) })
        }
    }
}
