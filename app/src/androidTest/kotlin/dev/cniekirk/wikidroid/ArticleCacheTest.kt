package dev.cniekirk.wikidroid

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.getOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** The article cache through the real graph: Room on the bundled driver, and the mock server as the wiki. */
class ArticleCacheTest {
    @get:Rule
    val reset = ResetAppStateRule()

    /**
     * The mock answers every `parse` request with the Diamond page, so any other title behaves like a redirect to
     * it. Reading it a second time must find that copy without downloading the page again: the only request
     * is the cheap check of the current revision.
     */
    @Test
    fun aRedirectThatWasReadOnceOpensFromTheCache() {
        runBlocking {
            val articles = testApp.graph.articleRepository
            drainRequests()

            val first = articles.getArticle("Dirt block").toList()
            assertThat(first.map { it.getOrNull()?.title }).containsExactly("Diamond")
            assertThat(drainRequests()).containsExactly("parse")

            val second = articles.getArticle("Dirt block").toList()

            assertThat(second.map { it.getOrNull()?.title }).containsExactly("Diamond")
            assertThat(drainRequests()).containsExactly("query")
        }
    }

    /** The API action of every request the mock server has seen since the last call. */
    private fun drainRequests(): List<String?> =
        buildList {
            while (true) {
                val request = testApp.server.takeRequest(QUIET_MILLIS, TimeUnit.MILLISECONDS) ?: break
                add(request.url.queryParameter("action"))
            }
        }

    private companion object {
        const val QUIET_MILLIS = 100L
    }
}
