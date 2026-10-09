package dev.cniekirk.wikidroid.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every destination in the app. Feature modules don't depend on each other, so they navigate
 * by pushing one of these keys. The hierarchy is sealed so the whole back stack serialises
 * without any registration.
 */
@Serializable
sealed interface WikiKey : NavKey

/** The five bottom-bar destinations. Each owns a back stack. */
@Serializable
sealed interface TopLevelKey : WikiKey

@Serializable
data object ExploreKey : TopLevelKey

@Serializable
data object SearchKey : TopLevelKey

@Serializable
data object SeedMapKey : TopLevelKey

@Serializable
data object LibraryKey : TopLevelKey

@Serializable
data object SettingsKey : TopLevelKey

/** A category's member list. [title] is the page title without the `Category:` prefix. */
@Serializable
data class CategoryKey(
    val title: String,
) : WikiKey

/** An article, optionally scrolled to the section [anchor]. */
@Serializable
data class ArticleKey(
    val title: String,
    val anchor: String? = null,
) : WikiKey

/** The About screen, pushed from Settings: version, attribution, disclaimer and licences. */
@Serializable
data object AboutKey : WikiKey

/** The tabs in bottom-bar order. */
val TopLevelKeys: List<TopLevelKey> = listOf(ExploreKey, SearchKey, SeedMapKey, LibraryKey, SettingsKey)
