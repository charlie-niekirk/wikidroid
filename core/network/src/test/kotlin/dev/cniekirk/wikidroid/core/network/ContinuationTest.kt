package dev.cniekirk.wikidroid.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class ContinuationTest {
    @Test
    fun `round trips numbers and strings with separators`() {
        val token =
            Continuation.encode(mapOf("gsroffset" to JsonPrimitive(20), "continue" to JsonPrimitive("gsroffset||")))

        assertThat(Continuation.decode(token)).containsExactly("gsroffset", "20", "continue", "gsroffset||")
    }

    @Test
    fun `no continuation encodes to null and decodes to nothing`() {
        assertThat(Continuation.encode(null)).isNull()
        assertThat(Continuation.encode(emptyMap())).isNull()
        assertThat(Continuation.decode(null)).isEmpty()
    }
}
