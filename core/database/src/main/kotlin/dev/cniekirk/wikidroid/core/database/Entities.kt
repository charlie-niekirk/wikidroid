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

/**
 * A place the player marked on the seed map, kept per seed and dimension. [category] and [color] are the
 * model's enum names; [dimension] is the seed map's dimension name.
 */
@Entity(tableName = "points_of_interest", indices = [Index("seed", "dimension")])
data class PointOfInterestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seed: Long,
    val dimension: String,
    val x: Int,
    val z: Int,
    val y: Int?,
    val name: String?,
    val category: String,
    val color: String,
    val note: String?,
    val createdAt: Long,
)

/** A bookmark or history row joined with whether its article is cached for offline reading. */
data class LibraryRow(
    val title: String,
    val displayTitle: String,
    val thumbnailUrl: String?,
    val timestamp: Long,
    val isAvailableOffline: Boolean,
)
