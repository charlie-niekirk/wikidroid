package dev.cniekirk.wikidroid

import androidx.test.platform.app.InstrumentationRegistry
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

/**
 * Answers minecraft.wiki's API from the trimmed real responses in `core/testing/src/main/resources/fixtures`
 * (packaged into the test APK's assets by `app/build.gradle.kts`). Requests are matched on the API action
 * and generator, not the full URL, so the tests don't depend on parameter order.
 */
class FixtureDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val fixture = fixtureFor(request)
        return if (fixture == null) {
            MockResponse
                .Builder()
                .code(404)
                .body("No fixture for ${request.url}")
                .build()
        } else {
            MockResponse
                .Builder()
                .code(200)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .body(readFixture(fixture))
                .build()
        }
    }

    private fun fixtureFor(request: RecordedRequest): String? {
        val url = request.url
        val path = url.encodedPath
        return when {
            path.endsWith("/rest.php/v1/search/title") -> {
                "rest_title_search.json"
            }

            !path.endsWith("/api.php") -> {
                null
            }

            else -> {
                when (url.queryParameter("action")) {
                    "parse" -> "parse_diamond.json"
                    "expandtemplates" -> "latest_versions.json"
                    "query" -> queryFixture(url.queryParameter("generator"), url.queryParameter("gsrsearch"))
                    else -> null
                }
            }
        }
    }

    private fun queryFixture(
        generator: String?,
        searchTerm: String?,
    ): String? =
        when (generator) {
            "search" -> {
                if (searchTerm.orEmpty().contains(
                        "creeper",
                        ignoreCase = true,
                    )
                ) {
                    "search_creeper.json"
                } else {
                    "search_empty.json"
                }
            }

            "categorymembers" -> {
                "category_mobs.json"
            }

            "random" -> {
                "random.json"
            }

            null -> {
                "revision_info.json"
            }

            else -> {
                null
            }
        }

    private fun readFixture(name: String): String =
        InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open("network/$name")
            .use { it.readBytes().decodeToString() }
}
