package dev.cniekirk.wikidroid.core.data

import android.app.Application
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.getOrNull
import dev.cniekirk.wikidroid.core.database.di.SqliteDriverProviders
import dev.cniekirk.wikidroid.core.network.WikiBaseUrl
import dev.cniekirk.wikidroid.core.testing.Fixtures
import dev.cniekirk.wikidroid.core.testing.RobolectricTest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Test

@BindingContainer
@ContributesTo(AppScope::class, replaces = [SqliteDriverProviders::class])
object TestSqliteDriverProviders {
    @Provides
    fun provideSqliteDriver(): SQLiteDriver = AndroidSQLiteDriver()
}

/** Every module's real providers, as the app graph will assemble them, with only the SQLite driver swapped. */
@DependencyGraph(AppScope::class)
interface DataTestGraph {
    val searchRepository: SearchRepository
    val articleRepository: ArticleRepository
    val categoryRepository: CategoryRepository
    val libraryRepository: LibraryRepository
    val settingsRepository: SettingsRepository
    val wikiInfoRepository: WikiInfoRepository

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
            @Provides @WikiBaseUrl baseUrl: HttpUrl,
        ): DataTestGraph
    }
}

class DataGraphTest : RobolectricTest() {
    private lateinit var server: MockWebServer
    private lateinit var graph: DataTestGraph

    @Before
    fun createGraph() {
        server = MockWebServer().apply { start() }
        graph =
            createGraphFactory<DataTestGraph.Factory>().create(
                application = ApplicationProvider.getApplicationContext(),
                baseUrl = server.url("/"),
            )
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun `repositories are singletons`() {
        assertThat(graph.articleRepository).isSameInstanceAs(graph.articleRepository)
        assertThat(graph.libraryRepository).isSameInstanceAs(graph.libraryRepository)
    }

    @Test
    fun `an article downloads through the real network, parser and database, then opens from the cache`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().body(Fixtures.read("network/parse_diamond.json")).build())

            val first =
                graph.articleRepository
                    .getArticle("Diamond")
                    .toList()
                    .single()
                    .getOrNull()!!

            assertThat(first.title).isEqualTo("Diamond")
            assertThat(first.revisionId).isEqualTo(3825498)
            assertThat(first.sections).isNotEmpty()
            assertThat(first.pageUrl).isEqualTo(server.url("/w/Diamond").toString())

            // Offline: the server has no more answers, so the freshness check fails, but the cached copy is served.
            val offline = graph.articleRepository.getArticle("Diamond").toList()

            assertThat(offline).hasSize(1)
            assertThat(offline.single().getOrNull()!!.sections).isEqualTo(first.sections)
        }

    @Test
    fun `a cached article makes a bookmark available offline`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().body(Fixtures.read("network/parse_diamond.json")).build())
            val article =
                graph.articleRepository
                    .getArticle("Diamond")
                    .first()
                    .getOrNull()!!

            graph.libraryRepository.addBookmark(article.toSummary())

            assertThat(
                graph.libraryRepository.bookmarks
                    .first()
                    .single()
                    .isAvailableOffline,
            ).isTrue()
        }
}
