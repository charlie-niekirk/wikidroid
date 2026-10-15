package dev.cniekirk.wikidroid.core.database

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        CachedArticleEntity::class,
        ArticleAliasEntity::class,
        PointOfInterestEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class WikiDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao

    abstract fun historyDao(): HistoryDao

    abstract fun cachedArticleDao(): CachedArticleDao

    abstract fun pointOfInterestDao(): PointOfInterestDao

    companion object {
        const val NAME = "wikidroid.db"
    }
}
