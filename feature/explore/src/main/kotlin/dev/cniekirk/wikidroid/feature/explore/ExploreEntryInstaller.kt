package dev.cniekirk.wikidroid.feature.explore

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.cniekirk.wikidroid.core.navigation.ArticleKey
import dev.cniekirk.wikidroid.core.navigation.CategoryKey
import dev.cniekirk.wikidroid.core.navigation.EntryProviderInstaller
import dev.cniekirk.wikidroid.core.navigation.ExploreKey
import dev.cniekirk.wikidroid.core.navigation.Navigator
import dev.cniekirk.wikidroid.core.ui.pane.WikiPanes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Registers the Explore tab and the category screen. */
@Inject
@ContributesIntoSet(AppScope::class)
class ExploreEntryInstaller : EntryProviderInstaller {
    override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
        entry<ExploreKey>(metadata = WikiPanes.list()) {
            ExploreRoute(
                onOpenArticle = { navigator.navigate(ArticleKey(it)) },
                onOpenCategory = { navigator.navigate(CategoryKey(it)) },
            )
        }
        entry<CategoryKey>(metadata = WikiPanes.list()) { key ->
            CategoryDetailRoute(
                title = key.title,
                onOpenArticle = { navigator.navigate(ArticleKey(it)) },
                onOpenCategory = { navigator.navigate(CategoryKey(it)) },
                onNavigateBack = { navigator.goBack() },
            )
        }
    }
}
