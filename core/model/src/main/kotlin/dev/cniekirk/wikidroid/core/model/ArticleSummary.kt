package dev.cniekirk.wikidroid.core.model

import androidx.compose.runtime.Immutable

/** A page as shown in lists: search results, category members, bookmarks. */
@Immutable
data class ArticleSummary(
    val title: String,
    val displayTitle: String = title,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val pageUrl: String? = null,
)
