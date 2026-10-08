package dev.cniekirk.wikidroid

import mockwebserver3.MockWebServer
import okhttp3.HttpUrl

/**
 * The application the instrumented tests run in. It builds the real [AppGraph] with a [MockWebServer] as the
 * wiki, answered by [FixtureDispatcher]. Tests reach the server through [server] to count or change requests.
 */
class TestWikiApp : WikiDroidApp() {
    val server: MockWebServer = MockWebServer().apply { dispatcher = FixtureDispatcher() }

    override fun baseUrl(): HttpUrl {
        server.start()
        return server.url("/")
    }
}
