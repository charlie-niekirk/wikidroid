package dev.cniekirk.wikidroid.core.database

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

// Timestamps are epoch milliseconds, so no type converters are needed. `title` is the canonical
// (redirect-resolved) page title, the same key in all three tables.

@Entity(tableName = "bookmarks", indices = [Index("savedAt")])
data class BookmarkEntity(
    @PrimaryKey val title: String,
    val displayTitle: String,
    val thumbnailUrl: String?,
    val savedAt: Long,
)

@Entity(tableName = "history", indices = [Index("viewedAt")])
data class HistoryEntity(
    @PrimaryKey val title: String,
    val displayTitle: String,
    val thumbnailUrl: String?,
    val viewedAt: Long,
)

/** The raw `action=parse` HTML, so parser upgrades also apply to pages that are already cached. */
@Entity(tableName = "cached_articles", indices = [Index("fetchedAt")])
data class CachedArticleEntity(
    @PrimaryKey val title: String,
    val revisionId: Long,
    val html: String,
    val fetchedAt: Long,
)

/** A bookmark or history row joined with whether its article is cached for offline reading. */
data class LibraryRow(
    val title: String,
    val displayTitle: String,
    val thumbnailUrl: String?,
    val timestamp: Long,
    val isAvailableOffline: Boolean,
)
