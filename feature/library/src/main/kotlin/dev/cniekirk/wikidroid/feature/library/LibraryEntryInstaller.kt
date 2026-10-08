package dev.cniekirk.wikidroid.feature.library

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.cniekirk.wikidroid.core.navigation.ArticleKey
import dev.cniekirk.wikidroid.core.navigation.EntryProviderInstaller
import dev.cniekirk.wikidroid.core.navigation.LibraryKey
import dev.cniekirk.wikidroid.core.navigation.Navigator
import dev.cniekirk.wikidroid.core.ui.pane.WikiPanes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Registers the Library tab. */
@Inject
@ContributesIntoSet(AppScope::class)
class LibraryEntryInstaller : EntryProviderInstaller {
    override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
        entry<LibraryKey>(metadata = WikiPanes.list()) {
            LibraryRoute(onOpenArticle = { navigator.navigate(ArticleKey(it)) })
        }
    }
}
