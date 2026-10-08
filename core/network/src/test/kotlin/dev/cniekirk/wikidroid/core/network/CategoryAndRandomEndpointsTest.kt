package dev.cniekirk.wikidroid.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CategoryAndRandomEndpointsTest : NetworkTest() {
    @Test
    fun `categoryMembers returns pages and subcategories`() =
        runTest {
            enqueueFixture("category_mobs.json")

            val result = dataSource.categoryMembers("Category:Mobs").successData()

            assertThat(result.pages.map { it.title }).containsAtLeast("Animal", "Monster", "Category:Boss mobs")
            val subcategories = result.pages.filter { it.ns == 14 }
            assertThat(subcategories.map { it.title }).containsExactly("Category:Boss mobs", "Category:End mobs")
            assertThat(result.pages.first { it.title == "Animal" }.thumbnail).isNotNull()
            assertThat(result.continuation).isNotNull()
        }

    @Test
    fun `categoryMembers requests namespaces 0 and 14`() =
        runTest {
            enqueueFixture("category_mobs.json")

            dataSource.categoryMembers("Category:Hostile mobs")

            val url = takeRequest().url
            assertThat(url.queryParameter("generator")).isEqualTo("categorymembers")
            assertThat(url.queryParameter("gcmtitle")).isEqualTo("Category:Hostile mobs")
            assertThat(url.queryParameter("gcmnamespace")).isEqualTo("0|14")
            assertThat(url.queryParameter("gcmlimit")).isEqualTo("20")
            assertThat(url.queryParameter("prop")).isEqualTo("pageimages|extracts|info")
        }

    @Test
    fun `categoryMembers continues with the gcmcontinue token`() =
        runTest {
            enqueueFixture("category_mobs.json")
            enqueueBody("""{"batchcomplete":true,"query":{"pages":[{"pageid":2,"ns":0,"title":"Zombie"}]}}""")

            val first = dataSource.categoryMembers("Category:Mobs").successData()
            takeRequest()
            val last = dataSource.categoryMembers("Category:Mobs", first.continuation).successData()

            val url = takeRequest().url
            assertThat(url.queryParameter("gcmcontinue")).isEqualTo("subcat|464c59494e47204d4f4253|146949")
            assertThat(url.queryParameter("continue")).isEqualTo("gcmcontinue||")
            assertThat(last.pages.map { it.title }).containsExactly("Zombie")
            assertThat(last.continuation).isNull()
        }

    @Test
    fun `randomPages returns the requested pages`() =
        runTest {
            enqueueFixture("random.json")

            val pages = dataSource.randomPages(count = 3).successData()

            assertThat(pages.map { it.title }).containsExactly("Drops")
            val url = takeRequest().url
            assertThat(url.queryParameter("generator")).isEqualTo("random")
            assertThat(url.queryParameter("grnnamespace")).isEqualTo("0")
            assertThat(url.queryParameter("grnfilterredir")).isEqualTo("nonredirects")
            assertThat(url.queryParameter("grnlimit")).isEqualTo("3")
        }
}
