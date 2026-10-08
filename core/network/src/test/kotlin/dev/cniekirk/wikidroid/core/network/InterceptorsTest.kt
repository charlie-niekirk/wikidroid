package dev.cniekirk.wikidroid.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okhttp3.Request
import org.junit.Test

class InterceptorsTest : NetworkTest() {
    @Test
    fun `every request identifies the app`() =
        runTest {
            enqueueFixture("rest_title_search_empty.json")
            enqueueFixture("search_empty.json")

            dataSource.autocomplete("x")
            dataSource.searchPages("x")

            repeat(2) {
                assertThat(takeRequest().headers["User-Agent"])
                    .matches("""WikiDroid/\S+ \(Android; https://github\.com/charlie-niekirk/wikidroid\)""")
            }
        }

    @Test
    fun `user agent format`() {
        assertThat(wikiUserAgent("1.2.3"))
            .isEqualTo("WikiDroid/1.2.3 (Android; https://github.com/charlie-niekirk/wikidroid)")
    }

    @Test
    fun `api php calls get format json and formatversion 2`() =
        runTest {
            enqueueFixture("search_empty.json")

            dataSource.searchPages("x")

            val url = takeRequest().url
            assertThat(url.queryParameterValues("format")).containsExactly("json")
            assertThat(url.queryParameterValues("formatversion")).containsExactly("2")
        }

    @Test
    fun `explicit format parameters are not duplicated`() {
        server.enqueue(MockResponse.Builder().body("{}").build())
        val request =
            Request
                .Builder()
                .url(server.url("/api.php?action=query&format=json&formatversion=1"))
                .build()

        graph.okHttpClient
            .newCall(request)
            .execute()
            .close()

        val url = takeRequest().url
        assertThat(url.queryParameterValues("format")).containsExactly("json")
        assertThat(url.queryParameterValues("formatversion")).containsExactly("1")
    }
}
