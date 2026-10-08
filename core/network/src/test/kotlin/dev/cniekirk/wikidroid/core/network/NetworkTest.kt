package dev.cniekirk.wikidroid.core.network

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.testing.Fixtures
import dev.cniekirk.wikidroid.core.testing.RobolectricTest
import dev.zacsweers.metro.createGraphFactory
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before

/** Base for tests that talk to a [MockWebServer] through the real Metro-built network stack. */
abstract class NetworkTest : RobolectricTest() {
    protected lateinit var server: MockWebServer
    protected lateinit var graph: NetworkTestGraph

    protected val dataSource: WikiRemoteDataSource get() = graph.dataSource

    @Before
    fun startServer() {
        server = MockWebServer().apply { start() }
        graph =
            createGraphFactory<NetworkTestGraph.Factory>().create(
                application = ApplicationProvider.getApplicationContext<Application>(),
                baseUrl = server.url("/"),
            )
    }

    @After
    fun stopServer() {
        server.close()
    }

    protected fun enqueueFixture(
        name: String,
        code: Int = 200,
    ) {
        server.enqueue(
            MockResponse
                .Builder()
                .code(code)
                .body(Fixtures.read("network/$name"))
                .build(),
        )
    }

    protected fun enqueueBody(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(
            MockResponse
                .Builder()
                .code(code)
                .body(body)
                .build(),
        )
    }

    protected fun takeRequest(): RecordedRequest = checkNotNull(server.takeRequest()) { "No request was made" }

    protected fun <T> Result<T, DataError>.successData(): T = (this as Result.Success).data

    protected fun Result<*, DataError>.failure(): DataError = (this as Result.Failure).error
}
