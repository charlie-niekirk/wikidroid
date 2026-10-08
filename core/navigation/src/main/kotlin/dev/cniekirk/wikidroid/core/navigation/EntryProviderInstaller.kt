package dev.cniekirk.wikidroid.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * How a feature module registers its screens. Each feature contributes one implementation
 * (`@ContributesIntoSet(AppScope::class)`) and `:app` installs all of them into a single `entryProvider`.
 *
 * ```
 * @Inject
 * @ContributesIntoSet(AppScope::class)
 * class ExploreEntryInstaller : EntryProviderInstaller {
 *     override fun EntryProviderScope<NavKey>.install(navigator: Navigator) {
 *         entry<ExploreKey> { ExploreRoute(onOpenArticle = { navigator.navigate(ArticleKey(it)) }) }
 *     }
 * }
 * ```
 */
fun interface EntryProviderInstaller {
    fun EntryProviderScope<NavKey>.install(navigator: Navigator)
}
