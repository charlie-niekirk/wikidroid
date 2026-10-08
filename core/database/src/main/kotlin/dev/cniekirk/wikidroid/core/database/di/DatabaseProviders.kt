package dev.cniekirk.wikidroid.core.database.di

import android.app.Application
import androidx.room3.Room
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.cniekirk.wikidroid.core.common.IoDispatcher
import dev.cniekirk.wikidroid.core.database.BookmarkDao
import dev.cniekirk.wikidroid.core.database.CachedArticleDao
import dev.cniekirk.wikidroid.core.database.HistoryDao
import dev.cniekirk.wikidroid.core.database.WikiDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The SQLite engine behind Room. Split out so host-JVM tests can replace it: the bundled library only
 * ships native code for Android ABIs.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object SqliteDriverProviders {
    @Provides
    fun provideSqliteDriver(): SQLiteDriver = BundledSQLiteDriver()
}

/** Requires `Application` from the graph's factory. */
@BindingContainer
@ContributesTo(AppScope::class)
object DatabaseProviders {
    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(
        application: Application,
        driver: SQLiteDriver,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): WikiDatabase =
        Room
            .databaseBuilder(application, WikiDatabase::class.java, WikiDatabase.NAME)
            .setDriver(driver)
            .setQueryCoroutineContext(ioDispatcher)
            .build()

    @Provides
    fun provideBookmarkDao(database: WikiDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideHistoryDao(database: WikiDatabase): HistoryDao = database.historyDao()

    @Provides
    fun provideCachedArticleDao(database: WikiDatabase): CachedArticleDao = database.cachedArticleDao()
}
