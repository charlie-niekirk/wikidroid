package dev.cniekirk.wikidroid.core.database

import android.app.Application
import androidx.room3.Room
import androidx.room3.useReaderConnection
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
    protected val points get() = database.pointOfInterestDao()

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

    /** Runs [sql] and returns its first column of its first row, for asserting on what the DAOs don't expose. */
    protected suspend fun scalar(sql: String): Long =
        database.useReaderConnection { connection ->
            connection.usePrepared(sql) { statement ->
                check(statement.step()) { "No row for $sql" }
                statement.getLong(0)
            }
        }

    protected fun bookmark(
        title: String,
        savedAt: Long = 0,
    ) = BookmarkEntity(title, displayTitle = title.replace('_', ' '), thumbnailUrl = null, savedAt = savedAt)

    protected fun visit(
        title: String,
        viewedAt: Long = 0,
    ) = HistoryEntity(title, displayTitle = title.replace('_', ' '), thumbnailUrl = null, viewedAt = viewedAt)

    protected fun point(
        seed: Long = 262,
        dimension: String = "OVERWORLD",
        name: String? = "Base",
        createdAt: Long = 0,
    ) = PointOfInterestEntity(
        seed = seed,
        dimension = dimension,
        x = 10,
        z = -20,
        y = 64,
        name = name,
        category = "BASE",
        color = "BLUE",
        note = null,
        createdAt = createdAt,
    )

    protected fun cached(
        title: String,
        revisionId: Long = 1,
        fetchedAt: Long = 0,
        html: String = "<p>$title</p>",
    ) = CachedArticleEntity(title, revisionId, html, fetchedAt)
}
