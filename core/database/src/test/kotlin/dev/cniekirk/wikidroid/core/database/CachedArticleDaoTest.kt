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
}
