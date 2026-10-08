package dev.cniekirk.wikidroid.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [BookmarkEntity::class, HistoryEntity::class, CachedArticleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WikiDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao

    abstract fun historyDao(): HistoryDao

    abstract fun cachedArticleDao(): CachedArticleDao

    companion object {
        const val NAME = "wikidroid.db"
    }
}
