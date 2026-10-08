package dev.cniekirk.wikidroid.core.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    /** Newest first. Re-emits when a bookmarked page is cached or evicted, so the offline badge stays right. */
    @Query(
        """
        SELECT b.title AS title, b.displayTitle AS displayTitle, b.thumbnailUrl AS thumbnailUrl,
               b.savedAt AS timestamp, c.title IS NOT NULL AS isAvailableOffline
        FROM bookmarks b LEFT JOIN cached_articles c ON c.title = b.title
        ORDER BY b.savedAt DESC
        """,
    )
    fun observeAll(): Flow<List<LibraryRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE title = :title)")
    fun observeIsBookmarked(title: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE title = :title")
    suspend fun delete(title: String)
}

@Dao
interface HistoryDao {
    /** Most recently viewed first. */
    @Query(
        """
        SELECT h.title AS title, h.displayTitle AS displayTitle, h.thumbnailUrl AS thumbnailUrl,
               h.viewedAt AS timestamp, c.title IS NOT NULL AS isAvailableOffline
        FROM history h LEFT JOIN cached_articles c ON c.title = h.title
        ORDER BY h.viewedAt DESC
        """,
    )
    fun observeAll(): Flow<List<LibraryRow>>

    /** Viewing a page again replaces its row, which moves it to the top. */
    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE title = :title")
    suspend fun delete(title: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface CachedArticleDao {
    @Query("SELECT * FROM cached_articles WHERE title = :title")
    suspend fun get(title: String): CachedArticleEntity?

    @Upsert
    suspend fun upsert(article: CachedArticleEntity)

    @Query("SELECT COUNT(*) FROM cached_articles")
    suspend fun count(): Int

    @Query("DELETE FROM cached_articles WHERE title = :title")
    suspend fun delete(title: String)

    @Query("DELETE FROM cached_articles")
    suspend fun clear()

    /**
     * Keeps every bookmarked article plus the [keep] most recently fetched of the rest, and deletes
     * the remainder. `pruneUnbookmarked(0)` clears everything that isn't bookmarked.
     */
    @Query(
        """
        DELETE FROM cached_articles
        WHERE title NOT IN (SELECT title FROM bookmarks)
          AND title NOT IN (
            SELECT title FROM cached_articles
            WHERE title NOT IN (SELECT title FROM bookmarks)
            ORDER BY fetchedAt DESC
            LIMIT :keep
          )
        """,
    )
    suspend fun pruneUnbookmarked(keep: Int)
}
