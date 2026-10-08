package dev.cniekirk.wikidroid.feature.article

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.cniekirk.wikidroid.core.navigation.ArticleKey
import dev.cniekirk.wikidroid.core.navigation.EntryProviderInstaller
import dev.cniekirk.wikidroid.core.navigation.Navigator
import dev.cniekirk.wikidroid.core.ui.pane.WikiPanes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Registers the article screen, which replaces the `:app` placeholder for [ArticleKey]. */
@Inject
@ContributesIntoSet(AppScope::class)
class ArticleEntryInstaller : EntryProviderInstaller {
    override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
        // The detail role keeps lists and the article side by side on wide windows.
        entry<ArticleKey>(metadata = WikiPanes.detail()) { key ->
            ArticleRoute(
                title = key.title,
                anchor = key.anchor,
                onOpenArticle = { title, anchor -> navigator.navigate(ArticleKey(title, anchor)) },
                onNavigateBack = { navigator.goBack() },
            )
        }
    }
}
