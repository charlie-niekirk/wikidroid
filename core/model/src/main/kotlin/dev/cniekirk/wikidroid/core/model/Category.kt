package dev.cniekirk.wikidroid.core.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/** A wiki category. [name] has no `Category:` prefix. */
@Immutable
data class Category(
    val name: String,
    val displayName: String = name.replace('_', ' '),
) {
    val pageTitle: String get() = "$PREFIX$name"

    companion object {
        const val PREFIX = "Category:"

        fun fromPageTitle(pageTitle: String): Category = Category(pageTitle.removePrefix(PREFIX))
    }
}

@Immutable
sealed interface CategoryMember {
    data class Page(
        val summary: ArticleSummary,
    ) : CategoryMember

    data class Subcategory(
        val category: Category,
    ) : CategoryMember
}

/** One page of a paged listing. A `null` [continuation] means there is nothing further to load. */
@Immutable
data class Paged<out T>(
    val items: ImmutableList<T>,
    val continuation: String?,
) {
    val hasMore: Boolean get() = continuation != null
}
