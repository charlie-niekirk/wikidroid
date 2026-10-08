package dev.cniekirk.wikidroid.core.network

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.testing.Fixtures
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import org.junit.Test

class ArticleEndpointsTest : NetworkTest() {
    @Test
    fun `parseArticle decodes the page`() =
        runTest {
            enqueueFixture("parse_diamond.json")

            val page = dataSource.parseArticle("Diamond").successData()

            assertThat(page.title).isEqualTo("Diamond")
            assertThat(page.pageId).isEqualTo(31051)
            assertThat(page.revisionId).isEqualTo(3825498)
            assertThat(page.displayTitle).isEqualTo("""<span class="mw-page-title-main">Diamond</span>""")
            assertThat(page.text).contains("""<section class="mf-section-0" id="mf-section-0">""")
            assertThat(page.sections.first().line).isEqualTo("Obtaining")
            assertThat(page.sections.first().level).isEqualTo("2")
            assertThat(page.sections.first().anchor).isEqualTo("Obtaining")
            assertThat(page.categories.first().category).isEqualTo("Resources_with_invalid_renewability")
            assertThat(page.categories.first().hidden).isTrue()
            assertThat(page.categories[1].hidden).isFalse()
        }

    @Test
    fun `parseArticle requests mobile html with redirects and a short cache lifetime`() =
        runTest {
            enqueueFixture("parse_diamond.json")

            dataSource.parseArticle("Diamond ore")

            val url = takeRequest().url
            assertThat(url.queryParameter("action")).isEqualTo("parse")
            assertThat(url.queryParameter("page")).isEqualTo("Diamond ore")
            assertThat(url.queryParameter("prop")).isEqualTo("text|sections|displaytitle|categories|revid")
            assertThat(url.queryParameter("mobileformat")).isEqualTo("1")
            assertThat(url.queryParameter("disableeditsection")).isEqualTo("1")
            assertThat(url.queryParameter("disabletoc")).isEqualTo("1")
            assertThat(url.queryParameter("redirects")).isEqualTo("1")
            assertThat(url.queryParameter("maxage")).isEqualTo("600")
            assertThat(url.queryParameter("smaxage")).isEqualTo("600")
        }

    @Test
    fun `parseArticle maps a missing title to NotFound`() =
        runTest {
            enqueueFixture("error_missing_title.json")

            assertThat(dataSource.parseArticle("Nope").failure()).isEqualTo(DataError.NotFound)
        }

    @Test
    fun `parseArticle without a parse object is a Parse error`() =
        runTest {
            enqueueBody("{}")

            assertThat(dataSource.parseArticle("Diamond").failure()).isEqualTo(DataError.Parse)
        }

    @Test
    fun `parseArticle responses are cached when the server allows it`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(Fixtures.read("network/parse_diamond.json"))
                    .addHeader("Cache-Control", "s-maxage=600, max-age=600, public")
                    .build(),
            )

            dataSource.parseArticle("Diamond").successData()
            dataSource.parseArticle("Diamond").successData()

            assertThat(server.requestCount).isEqualTo(1)
        }

    @Test
    fun `latestRevision follows the redirect to the current revision`() =
        runTest {
            enqueueFixture("revision_info.json")

            val revision = dataSource.latestRevision("Diamonds").successData()

            assertThat(revision).isEqualTo(PageRevision(title = "Diamond", revisionId = 3825498))
            val url = takeRequest().url
            assertThat(url.queryParameter("prop")).isEqualTo("info")
            assertThat(url.queryParameter("titles")).isEqualTo("Diamonds")
            assertThat(url.queryParameter("redirects")).isEqualTo("1")
        }

    @Test
    fun `latestRevision of a missing page is NotFound`() =
        runTest {
            enqueueFixture("revision_missing.json")

            assertThat(dataSource.latestRevision("NoSuchPageZZ").failure()).isEqualTo(DataError.NotFound)
        }

    @Test
    fun `latestVersions parses the expanded templates`() =
        runTest {
            enqueueFixture("latest_versions.json")

            val versions = dataSource.latestVersions().successData()

            assertThat(versions.java).isEqualTo("26.3")
            assertThat(versions.javaSnapshot).isEqualTo("26.4 Snapshot 3")
            assertThat(versions.bedrock).isEqualTo("26.52")
            assertThat(versions.bedrockPreview).isEqualTo("Preview 26.60.30")
            val url = takeRequest().url
            assertThat(url.queryParameter("action")).isEqualTo("expandtemplates")
            assertThat(url.queryParameter("prop")).isEqualTo("wikitext")
            assertThat(url.queryParameter("text"))
                .isEqualTo("{{Version|java}}|{{Version|java-snap}}|{{Version|bedrock}}|{{Version|bedrock-preview}}")
        }
}
