package dev.cniekirk.wikidroid.core.network

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ErrorMappingTest : NetworkTest() {
    @Test
    fun `an error body with HTTP 200 becomes an Api error`() =
        runTest {
            enqueueFixture("error_bad_action.json")

            val error = dataSource.searchPages("x").failure()

            assertThat(error).isInstanceOf(DataError.Api::class.java)
            error as DataError.Api
            assertThat(error.code).isEqualTo("badvalue")
            assertThat(error.info).contains("Unrecognized value")
        }

    @Test
    fun `HTTP errors carry their status code`() =
        runTest {
            enqueueBody("Forbidden", code = 403)
            enqueueBody("oops", code = 503)

            assertThat(dataSource.searchPages("x").failure()).isEqualTo(DataError.Network(403))
            assertThat(dataSource.autocomplete("x").failure()).isEqualTo(DataError.Network(503))
        }

    @Test
    fun `HTTP 404 is NotFound`() =
        runTest {
            enqueueBody("{}", code = 404)

            assertThat(dataSource.parseArticle("x").failure()).isEqualTo(DataError.NotFound)
        }

    @Test
    fun `malformed JSON is a Parse error`() =
        runTest {
            enqueueBody("<html>not json</html>")
            enqueueBody("""{"pages": 5}""")

            assertThat(dataSource.searchPages("x").failure()).isEqualTo(DataError.Parse)
            assertThat(dataSource.autocomplete("x").failure()).isEqualTo(DataError.Parse)
        }

    @Test
    fun `an unreachable server is a Network error`() =
        runTest {
            server.close()

            assertThat(dataSource.searchPages("x").failure()).isEqualTo(DataError.Network())
        }

    @Test
    fun `a corrupt continuation token is a Parse error`() =
        runTest {
            assertThat(dataSource.searchPages("x", continuation = "not-json").failure()).isEqualTo(DataError.Parse)
        }
}
