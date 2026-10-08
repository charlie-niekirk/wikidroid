package dev.cniekirk.wikidroid.core.database

import android.app.Application
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.database.di.SqliteDriverProviders
import dev.cniekirk.wikidroid.core.testing.RobolectricTest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

@BindingContainer
@ContributesTo(AppScope::class, replaces = [SqliteDriverProviders::class])
object TestSqliteDriverProviders {
    @Provides
    fun provideSqliteDriver(): SQLiteDriver = AndroidSQLiteDriver()
}

/** Builds the database through the real Metro providers, as the app graph will. */
@DependencyGraph(AppScope::class)
interface DatabaseTestGraph {
    val database: WikiDatabase
    val bookmarkDao: BookmarkDao
    val historyDao: HistoryDao
    val cachedArticleDao: CachedArticleDao

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
        ): DatabaseTestGraph
    }
}

class DatabaseProvidersTest : RobolectricTest() {
    private val graph =
        createGraphFactory<DatabaseTestGraph.Factory>().create(ApplicationProvider.getApplicationContext())

    @Test
    fun `providers build a working file backed database`() =
        runTest {
            graph.bookmarkDao.upsert(BookmarkEntity("Diamond", "Diamond", null, savedAt = 1))
            graph.historyDao.upsert(HistoryEntity("Diamond", "Diamond", null, viewedAt = 2))
            graph.cachedArticleDao.upsert(CachedArticleEntity("Diamond", 3, "<p/>", 4))

            assertThat(
                graph.bookmarkDao
                    .observeAll()
                    .first()
                    .single()
                    .isAvailableOffline,
            ).isTrue()
            assertThat(graph.historyDao.observeAll().first()).hasSize(1)
            graph.database.close()
        }

    @Test
    fun `the database is a singleton and the daos share it`() {
        assertThat(graph.database).isSameInstanceAs(graph.database)
        graph.database.close()
    }
}
