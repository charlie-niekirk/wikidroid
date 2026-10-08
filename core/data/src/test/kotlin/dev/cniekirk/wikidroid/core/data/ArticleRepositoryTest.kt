package dev.cniekirk.wikidroid.core.data

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.getOrNull
import dev.cniekirk.wikidroid.core.database.BookmarkEntity
import dev.cniekirk.wikidroid.core.database.CachedArticleEntity
import dev.cniekirk.wikidroid.core.model.Article
import dev.cniekirk.wikidroid.core.model.ArticleSection
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.network.PageRevision
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Test

class ArticleRepositoryTest : DatabaseRepositoryTest() {
    private val remote = FakeRemote()
    private val clock = FakeClock()
    private lateinit var repository: OfflineFirstArticleRepository

    private val cache get() = database.cachedArticleDao()

    @Before
    fun createRepository() {
        repository =
            OfflineFirstArticleRepository(
                remote = remote,
                cache = cache,
                parser = EchoParser,
                clock = clock,
                defaultDispatcher = Dispatchers.Unconfined,
                baseUrl = "https://minecraft.wiki/".toHttpUrl(),
            )
    }

    private suspend fun load(title: String): List<Result<Article, DataError>> = repository.getArticle(title).toList()

    private fun List<Result<Article, DataError>>.articles() = map { it.getOrNull() }

    @Test
    fun `without a cached copy the page is downloaded, cached and emitted`() =
        runTest {
            remote.parseResults["Diamond"] = Result.Success(parsedPage("Diamond", revisionId = 7, text = "<p>gem</p>"))

            val emissions = load("Diamond")

            assertThat(emissions).hasSize(1)
            val article = emissions.single().getOrNull()!!
            assertThat(article.title).isEqualTo("Diamond")
            assertThat(article.revisionId).isEqualTo(7)
            assertThat(article.pageUrl).isEqualTo("https://minecraft.wiki/w/Diamond")
            assertThat(cache.get("Diamond")).isEqualTo(CachedArticleEntity("Diamond", 7, "<p>gem</p>", 1_000_000))
        }

    @Test
    fun `a download failure with nothing cached is emitted as a failure`() =
        runTest {
            remote.parseResults["Diamond"] = Result.Failure(DataError.Network())

            val emissions = load("Diamond")

            assertThat(emissions).containsExactly(Result.Failure(DataError.Network()))
            assertThat(cache.count()).isEqualTo(0)
        }

    @Test
    fun `a cached copy is emitted first and an unchanged revision emits nothing more`() =
        runTest {
            cache.upsert(CachedArticleEntity("Diamond", 7, "<p>old</p>", 0))
            remote.revisionResults["Diamond"] = Result.Success(PageRevision("Diamond", 7))

            val emissions = load("Diamond")

            assertThat(emissions.articles().map { it?.revisionId }).containsExactly(7L)
            assertThat(remote.calls).containsExactly("revision:Diamond")
        }

    @Test
    fun `a newer revision is downloaded, cached and emitted after the cached copy`() =
        runTest {
            cache.upsert(CachedArticleEntity("Diamond", 7, "<p>old</p>", 0))
            remote.revisionResults["Diamond"] = Result.Success(PageRevision("Diamond", 8))
            remote.parseResults["Diamond"] = Result.Success(parsedPage("Diamond", revisionId = 8, text = "<p>new</p>"))

            val emissions = load("Diamond")

            assertThat(emissions.articles().map { it?.revisionId }).containsExactly(7L, 8L).inOrder()
            assertThat(cache.get("Diamond")).isEqualTo(CachedArticleEntity("Diamond", 8, "<p>new</p>", 1_000_000))
            assertThat(cache.count()).isEqualTo(1)
        }

    @Test
    fun `network failures after a cached copy was shown are swallowed`() =
        runTest {
            cache.upsert(CachedArticleEntity("Diamond", 7, "<p>old</p>", 0))

            // Offline: the freshness check fails.
            remote.revisionResults["Diamond"] = Result.Failure(DataError.Network())
            assertThat(load("Diamond").articles().map { it?.revisionId }).containsExactly(7L)

            // Online for the check, but the download fails.
            remote.revisionResults["Diamond"] = Result.Success(PageRevision("Diamond", 8))
            remote.parseResults["Diamond"] = Result.Failure(DataError.Network(503))
            assertThat(load("Diamond").articles().map { it?.revisionId }).containsExactly(7L)
            assertThat(cache.get("Diamond")!!.revisionId).isEqualTo(7)
        }

    @Test
    fun `a page that became a redirect is downloaded again under its new title`() =
        runTest {
            cache.upsert(CachedArticleEntity("Old name", 7, "<p>old</p>", 0))
            remote.revisionResults["Old name"] = Result.Success(PageRevision("New name", 7))
            remote.parseResults["Old name"] = Result.Success(parsedPage("New name", revisionId = 7))

            val emissions = load("Old name")

            assertThat(emissions.articles().map { it?.title }).containsExactly("Old name", "New name").inOrder()
            assertThat(cache.get("New name")).isNotNull()
        }

    @Test
    fun `redirects are cached under the canonical title`() =
        runTest {
            remote.parseResults["Dirt block"] = Result.Success(parsedPage("Dirt", revisionId = 3))

            val article = load("Dirt block").single().getOrNull()!!

            assertThat(article.title).isEqualTo("Dirt")
            assertThat(cache.get("Dirt")).isNotNull()
            assertThat(cache.get("Dirt block")).isNull()
        }

    @Test
    fun `underscores in the requested title are treated as spaces`() =
        runTest {
            cache.upsert(CachedArticleEntity("Diamond Ore", 4, "<p>ore</p>", 0))
            remote.revisionResults["Diamond Ore"] = Result.Success(PageRevision("Diamond Ore", 4))

            val article = load("Diamond_Ore").first().getOrNull()!!

            assertThat(article.title).isEqualTo("Diamond Ore")
            assertThat(article.pageUrl).isEqualTo("https://minecraft.wiki/w/Diamond_Ore")
        }

    @Test
    fun `a cached copy the parser rejects is downloaded again`() =
        runTest {
            cache.upsert(CachedArticleEntity("Diamond", 7, EchoParser.UNPARSEABLE, 0))
            remote.parseResults["Diamond"] = Result.Success(parsedPage("Diamond", revisionId = 8))

            val emissions = load("Diamond")

            assertThat(emissions.articles().map { it?.revisionId }).containsExactly(8L)
            assertThat(cache.get("Diamond")!!.revisionId).isEqualTo(8)
        }

    @Test
    fun `downloaded html the parser rejects is a parse error and is not cached`() =
        runTest {
            remote.parseResults["Diamond"] = Result.Success(parsedPage("Diamond", text = EchoParser.UNPARSEABLE))

            assertThat(load("Diamond")).containsExactly(Result.Failure(DataError.Parse))
            assertThat(cache.count()).isEqualTo(0)
        }

    @Test
    fun `unbookmarked articles beyond the limit are pruned and bookmarked ones are kept`() =
        runTest {
            database.bookmarkDao().upsert(BookmarkEntity("Title 0", "Title 0", null, savedAt = 0))
            repeat(MAX_CACHED_ARTICLES + 5) { index ->
                remote.parseResults["Title $index"] = Result.Success(parsedPage("Title $index"))
                clock.advanceBy(1000)
                load("Title $index")
            }

            // 105 downloads: the bookmarked Title 0 plus the 100 newest others.
            assertThat(cache.count()).isEqualTo(MAX_CACHED_ARTICLES + 1)
            assertThat(cache.get("Title 0")).isNotNull()
            assertThat(cache.get("Title 1")).isNull()
            assertThat(cache.get("Title 4")).isNull()
            assertThat(cache.get("Title 5")).isNotNull()
            assertThat(cache.get("Title ${MAX_CACHED_ARTICLES + 4}")).isNotNull()
        }

    @Test
    fun `clearCache keeps bookmarked articles`() =
        runTest {
            database.bookmarkDao().upsert(BookmarkEntity("Diamond", "Diamond", null, savedAt = 0))
            cache.upsert(CachedArticleEntity("Diamond", 1, "<p/>", 0))
            cache.upsert(CachedArticleEntity("Creeper", 1, "<p/>", 0))

            repository.clearCache()

            assertThat(cache.get("Diamond")).isNotNull()
            assertThat(cache.get("Creeper")).isNull()
        }

    @Test
    fun `the thumbnail is the infobox picture, else the first lead image`() =
        runTest {
            fun repositoryParsing(vararg blocks: ContentBlock) =
                OfflineFirstArticleRepository(
                    remote = remote,
                    cache = cache,
                    parser = { persistentListOf(ArticleSection(null, persistentListOf(*blocks))) },
                    clock = clock,
                    defaultDispatcher = Dispatchers.Unconfined,
                    baseUrl = "https://minecraft.wiki/".toHttpUrl(),
                )
            remote.parseResults["Diamond"] = Result.Success(parsedPage("Diamond"))
            val lead = ContentBlock.Image(url = "https://minecraft.wiki/lead.png")
            val infobox =
                ContentBlock.Infobox(
                    title = null,
                    images = persistentListOf(ContentBlock.Image(url = "https://minecraft.wiki/infobox.png")),
                    rows = persistentListOf(),
                )

            val withInfobox = repositoryParsing(lead, infobox).getArticle("Diamond").first().getOrNull()!!
            cache.clear()
            val withoutInfobox = repositoryParsing(lead).getArticle("Diamond").first().getOrNull()!!
            cache.clear()
            val withoutImages = repositoryParsing().getArticle("Diamond").first().getOrNull()!!

            assertThat(withInfobox.thumbnailUrl).isEqualTo("https://minecraft.wiki/infobox.png")
            assertThat(withoutInfobox.thumbnailUrl).isEqualTo("https://minecraft.wiki/lead.png")
            assertThat(withoutImages.thumbnailUrl).isNull()
        }
}
