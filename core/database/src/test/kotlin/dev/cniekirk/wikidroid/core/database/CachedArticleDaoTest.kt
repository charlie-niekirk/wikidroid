package dev.cniekirk.wikidroid.core.database

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CachedArticleDaoTest : DatabaseTest() {
    @Test
    fun `get returns what was stored`() =
        runTest {
            articles.upsert(cached("Diamond", revisionId = 42, fetchedAt = 7, html = "<p>hello</p>"))

            assertThat(articles.get("Diamond"))
                .isEqualTo(CachedArticleEntity("Diamond", revisionId = 42, html = "<p>hello</p>", fetchedAt = 7))
        }

    @Test
    fun `get of an unknown title is null`() =
        runTest {
            assertThat(articles.get("Nope")).isNull()
        }

    @Test
    fun `upsert replaces the revision and html`() =
        runTest {
            articles.upsert(cached("Diamond", revisionId = 1, html = "<p>old</p>"))
            articles.upsert(cached("Diamond", revisionId = 2, html = "<p>new</p>"))

            val article = articles.get("Diamond")

            assertThat(article?.revisionId).isEqualTo(2)
            assertThat(article?.html).isEqualTo("<p>new</p>")
            assertThat(articles.count()).isEqualTo(1)
        }

    @Test
    fun `large html survives a round trip`() =
        runTest {
            val html = "<p>" + "x".repeat(700_000) + "</p>"
            articles.upsert(cached("Diamond", html = html))

            assertThat(articles.get("Diamond")?.html).isEqualTo(html)
        }

    @Test
    fun `delete and clear remove entries`() =
        runTest {
            articles.upsert(cached("A"))
            articles.upsert(cached("B"))

            articles.delete("A")
            assertThat(articles.count()).isEqualTo(1)

            articles.clear()
            assertThat(articles.count()).isEqualTo(0)
        }

    @Test
    fun `pruning keeps the newest unbookmarked articles`() =
        runTest {
            (1..5).forEach { articles.upsert(cached("Page$it", fetchedAt = it.toLong())) }

            articles.pruneUnbookmarked(keep = 2)

            assertThat(articles.get("Page5")).isNotNull()
            assertThat(articles.get("Page4")).isNotNull()
            assertThat(articles.get("Page3")).isNull()
            assertThat(articles.get("Page2")).isNull()
            assertThat(articles.get("Page1")).isNull()
        }

    @Test
    fun `pruning never deletes bookmarked articles and they do not use up the quota`() =
        runTest {
            (1..5).forEach { articles.upsert(cached("Page$it", fetchedAt = it.toLong())) }
            bookmarks.upsert(bookmark("Page1"))
            bookmarks.upsert(bookmark("Page5"))

            articles.pruneUnbookmarked(keep = 1)

            // Bookmarked: Page1, Page5. Of the rest (2, 3, 4) only the newest (4) stays.
            assertThat(articles.count()).isEqualTo(3)
            assertThat(articles.get("Page1")).isNotNull()
            assertThat(articles.get("Page5")).isNotNull()
            assertThat(articles.get("Page4")).isNotNull()
        }

    @Test
    fun `pruning with a quota of zero clears everything unbookmarked`() =
        runTest {
            articles.upsert(cached("Kept"))
            articles.upsert(cached("Gone"))
            bookmarks.upsert(bookmark("Kept"))

            articles.pruneUnbookmarked(keep = 0)

            assertThat(articles.get("Kept")).isNotNull()
            assertThat(articles.get("Gone")).isNull()
        }

    @Test
    fun `pruning below the quota deletes nothing`() =
        runTest {
            articles.upsert(cached("A"))
            articles.upsert(cached("B"))

            articles.pruneUnbookmarked(keep = 100)

            assertThat(articles.count()).isEqualTo(2)
        }

    @Test
    fun `a redirect name resolves to the article stored for it`() =
        runTest {
            articles.storeWithAlias(cached("Dirt", revisionId = 3), requested = "Dirt block")

            assertThat(articles.get("Dirt block")).isNull()
            assertThat(articles.getByAlias("Dirt block")).isEqualTo(cached("Dirt", revisionId = 3))
            assertThat(articles.getByAlias("Dirt")).isNull()
        }

    @Test
    fun `storing under the article's own title adds no alias`() =
        runTest {
            articles.storeWithAlias(cached("Dirt"), requested = "Dirt")

            assertThat(articles.get("Dirt")).isNotNull()
            assertThat(articles.getByAlias("Dirt")).isNull()
        }

    @Test
    fun `storing under a redirect name deletes the stale copy cached under that name`() =
        runTest {
            articles.upsert(cached("Old name", revisionId = 1, html = "<p>stale</p>"))

            articles.storeWithAlias(cached("New name", revisionId = 2), requested = "Old name")

            assertThat(articles.get("Old name")).isNull()
            assertThat(articles.getByAlias("Old name")?.title).isEqualTo("New name")
            assertThat(articles.count()).isEqualTo(1)
        }

    @Test
    fun `a page that stopped being a redirect drops its alias`() =
        runTest {
            articles.storeWithAlias(cached("Dirt"), requested = "Dirt block")

            articles.storeWithAlias(cached("Dirt block", revisionId = 5), requested = "Dirt block")

            assertThat(articles.getByAlias("Dirt block")).isNull()
            assertThat(articles.get("Dirt block")?.revisionId).isEqualTo(5)
        }

    @Test
    fun `refreshing an article keeps the aliases that point at it`() =
        runTest {
            articles.storeWithAlias(cached("Dirt", revisionId = 1), requested = "Dirt block")

            articles.storeWithAlias(cached("Dirt", revisionId = 2), requested = "Dirt")

            // An upsert updates in place; a replacing insert would delete the row and cascade to the alias.
            assertThat(articles.getByAlias("Dirt block")?.revisionId).isEqualTo(2)
        }

    @Test
    fun `deleting, clearing and pruning an article remove its alias rows`() =
        runTest {
            articles.storeWithAlias(cached("A", fetchedAt = 1), requested = "A alias")
            articles.storeWithAlias(cached("B", fetchedAt = 2), requested = "B alias")
            articles.storeWithAlias(cached("C", fetchedAt = 3), requested = "C alias")
            assertThat(scalar("SELECT COUNT(*) FROM article_aliases")).isEqualTo(3)

            articles.delete("A")
            assertThat(scalar("SELECT COUNT(*) FROM article_aliases")).isEqualTo(2)

            articles.pruneUnbookmarked(keep = 1)
            assertThat(scalar("SELECT COUNT(*) FROM article_aliases")).isEqualTo(1)
            assertThat(articles.getByAlias("C alias")).isNotNull()

            articles.clear()
            assertThat(scalar("SELECT COUNT(*) FROM article_aliases")).isEqualTo(0)
        }

    @Test
    fun `a bookmarked article keeps its aliases through pruning`() =
        runTest {
            articles.storeWithAlias(cached("Dirt"), requested = "Dirt block")
            bookmarks.upsert(bookmark("Dirt"))

            articles.pruneUnbookmarked(keep = 0)

            assertThat(articles.getByAlias("Dirt block")).isNotNull()
        }
}
