package dev.cniekirk.wikidroid.core.database

import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The alias table on the driver the app ships with. The host-JVM DAO tests use Robolectric's SQLite, which can't
 * tell us whether the bundled one enforces the foreign key that deletes an article's aliases with it.
 */
@RunWith(AndroidJUnit4::class)
class ArticleAliasTest {
    private lateinit var database: WikiDatabase

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    WikiDatabase::class.java,
                ).setDriver(BundledSQLiteDriver())
                .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun deletingAnArticleDeletesItsAliases() =
        runBlocking {
            val articles = database.cachedArticleDao()
            articles.storeWithAlias(CachedArticleEntity("Dirt", 3, "<p>dirt</p>", 1), requested = "Dirt block")
            assertThat(aliasCount()).isEqualTo(1)
            assertThat(articles.getByAlias("Dirt block")?.title).isEqualTo("Dirt")

            articles.delete("Dirt")

            assertThat(aliasCount()).isEqualTo(0)
        }

    @Test
    fun refreshingAnArticleKeepsItsAliases() =
        runBlocking {
            val articles = database.cachedArticleDao()
            articles.storeWithAlias(CachedArticleEntity("Dirt", 3, "<p>old</p>", 1), requested = "Dirt block")

            articles.storeWithAlias(CachedArticleEntity("Dirt", 4, "<p>new</p>", 2), requested = "Dirt")

            assertThat(articles.getByAlias("Dirt block")?.revisionId).isEqualTo(4)
        }

    private suspend fun aliasCount(): Long =
        database.useReaderConnection { connection ->
            connection.usePrepared("SELECT COUNT(*) FROM article_aliases") { statement ->
                check(statement.step())
                statement.getLong(0)
            }
        }
}
