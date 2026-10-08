package dev.cniekirk.wikidroid.core.database

import android.app.Application
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.cniekirk.wikidroid.core.testing.RobolectricTest
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before

/**
 * Base for DAO tests: an in-memory [WikiDatabase]. The app uses `BundledSQLiteDriver`, whose native
 * library only exists for Android ABIs, so host-JVM tests run the same SQL on Robolectric's native
 * SQLite through `AndroidSQLiteDriver`. The bundled driver is covered by the instrumented tests.
 */
abstract class DatabaseTest : RobolectricTest() {
    protected lateinit var database: WikiDatabase

    protected val bookmarks get() = database.bookmarkDao()
    protected val history get() = database.historyDao()
    protected val articles get() = database.cachedArticleDao()

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Application>(),
                    WikiDatabase::class.java,
                ).setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    protected fun bookmark(
        title: String,
        savedAt: Long = 0,
    ) = BookmarkEntity(title, displayTitle = title.replace('_', ' '), thumbnailUrl = null, savedAt = savedAt)

    protected fun visit(
        title: String,
        viewedAt: Long = 0,
    ) = HistoryEntity(title, displayTitle = title.replace('_', ' '), thumbnailUrl = null, viewedAt = viewedAt)

    protected fun cached(
        title: String,
        revisionId: Long = 1,
        fetchedAt: Long = 0,
        html: String = "<p>$title</p>",
    ) = CachedArticleEntity(title, revisionId, html, fetchedAt)
}
