package dev.cniekirk.wikidroid.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchEndpointsTest : NetworkTest() {
    @Test
    fun `autocomplete decodes titles with thumbnails`() =
        runTest {
            enqueueFixture("rest_title_search.json")

            val pages = dataSource.autocomplete("diam", limit = 3).successData()

            assertThat(pages.map { it.title }).containsExactly("Diamond", "Diamond Ore", "Diamond Chicken").inOrder()
            assertThat(pages.first().key).isEqualTo("Diamond")
            assertThat(pages.first().thumbnail?.url).startsWith("https://minecraft.wiki/images/thumb/")
            assertThat(pages.first().description).isNull()
        }

    @Test
    fun `autocomplete sends the query to rest php without api params`() =
        runTest {
            enqueueFixture("rest_title_search.json")

            dataSource.autocomplete("diam & co", limit = 7)

            val url = takeRequest().url
            assertThat(url.encodedPath).isEqualTo("/rest.php/v1/search/title")
            assertThat(url.queryParameter("q")).isEqualTo("diam & co")
            assertThat(url.queryParameter("limit")).isEqualTo("7")
            assertThat(url.queryParameter("format")).isNull()
        }

    @Test
    fun `autocomplete with no matches is an empty list`() =
        runTest {
            enqueueFixture("rest_title_search_empty.json")

            assertThat(dataSource.autocomplete("zzqqxx").successData()).isEmpty()
        }

    @Test
    fun `autocomplete tolerates missing thumbnail and unknown fields`() =
        runTest {
            enqueueBody(
                """{"pages":[{"id":1,"key":"Foo","title":"Foo","excerpt":null,"description":null,""" +
                    """"thumbnail":null,"x":1}]}""",
            )

            val page = dataSource.autocomplete("foo").successData().single()

            assertThat(page.thumbnail).isNull()
        }

    @Test
    fun `searchPages decodes results in index order with a continuation`() =
        runTest {
            enqueueFixture("search_creeper.json")

            val result = dataSource.searchPages("creeper").successData()

            assertThat(result.pages.map { it.title }).containsExactly("Creeper", "Creeper Head", "Banner").inOrder()
            val creeper = result.pages.first()
            assertThat(creeper.pageId).isEqualTo(628)
            assertThat(creeper.thumbnail?.source).contains("Creeper_JE3_BE1.png")
            assertThat(creeper.thumbnail?.width).isEqualTo(160)
            assertThat(creeper.extract).startsWith("A creeper is a common hostile mob")
            assertThat(creeper.lastRevisionId).isEqualTo(3822896)
            assertThat(creeper.fullUrl).isEqualTo("https://minecraft.wiki/w/Creeper")
            assertThat(result.continuation).isNotNull()
        }

    @Test
    fun `searchPages orders results by their search index`() =
        runTest {
            enqueueBody(
                """{"query":{"pages":[
                    {"pageid":9,"title":"Third","index":3},
                    {"pageid":1,"title":"First","index":1},
                    {"pageid":5,"title":"Second","index":2}]}}""",
            )

            val titles =
                dataSource
                    .searchPages("x")
                    .successData()
                    .pages
                    .map { it.title }

            assertThat(titles).containsExactly("First", "Second", "Third").inOrder()
        }

    @Test
    fun `searchPages requests the documented parameters`() =
        runTest {
            enqueueFixture("search_creeper.json")

            dataSource.searchPages("iron golem")

            val url = takeRequest().url
            assertThat(url.encodedPath).isEqualTo("/api.php")
            assertThat(url.queryParameter("action")).isEqualTo("query")
            assertThat(url.queryParameter("generator")).isEqualTo("search")
            assertThat(url.queryParameter("gsrsearch")).isEqualTo("iron golem")
            assertThat(url.queryParameter("gsrnamespace")).isEqualTo("0")
            assertThat(url.queryParameter("gsrlimit")).isEqualTo("20")
            assertThat(url.queryParameter("prop")).isEqualTo("pageimages|extracts|info")
            assertThat(url.queryParameter("piprop")).isEqualTo("thumbnail")
            assertThat(url.queryParameter("pithumbsize")).isEqualTo("160")
            assertThat(url.queryParameter("exintro")).isNotNull()
            assertThat(url.queryParameter("explaintext")).isNotNull()
            assertThat(url.queryParameter("exsentences")).isEqualTo("2")
            assertThat(url.queryParameter("exlimit")).isEqualTo("max")
            assertThat(url.queryParameter("inprop")).isEqualTo("url")
            assertThat(url.queryParameter("format")).isEqualTo("json")
            assertThat(url.queryParameter("formatversion")).isEqualTo("2")
        }

    @Test
    fun `searchPages sends the continuation back unchanged`() =
        runTest {
            enqueueFixture("search_creeper.json")
            enqueueFixture("search_empty.json")

            val first = dataSource.searchPages("creeper").successData()
            takeRequest()
            val second = dataSource.searchPages("creeper", first.continuation).successData()

            val url = takeRequest().url
            assertThat(url.queryParameter("gsroffset")).isEqualTo("3")
            assertThat(url.queryParameter("continue")).isEqualTo("gsroffset||")
            assertThat(second.continuation).isNull()
        }

    @Test
    fun `searchPages with no results is an empty last page`() =
        runTest {
            enqueueFixture("search_empty.json")

            val result = dataSource.searchPages("zzqqxx").successData()

            assertThat(result.pages).isEmpty()
            assertThat(result.continuation).isNull()
        }
}
