package dev.cniekirk.wikidroid.core.datastore

import androidx.datastore.core.CorruptionException
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.Edition
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.model.UserPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class JsonSerializerTest {
    private val serializer = JsonSerializer(StoredUserData.serializer(), StoredUserData())

    private suspend fun read(json: String): StoredUserData =
        serializer.readFrom(ByteArrayInputStream(json.encodeToByteArray()))

    private suspend fun write(data: StoredUserData): String =
        ByteArrayOutputStream().also { serializer.writeTo(data, it) }.toString(Charsets.UTF_8)

    @Test
    fun `a value survives a write and read`() =
        runTest {
            val data =
                StoredUserData(
                    preferences =
                        UserPreferences(
                            themeMode = ThemeMode.Dark,
                            dynamicColor = false,
                            textScale = 1.25f,
                            preferredEdition = Edition.Bedrock,
                            saveHistory = false,
                        ),
                    recentSearches = listOf("creeper", "Iron Golem"),
                    savedSeeds = listOf(SavedSeed(Long.MIN_VALUE, "26.3", "Edge"), SavedSeed(262, "1.12")),
                )

            assertThat(read(write(data))).isEqualTo(data)
        }

    @Test
    fun `defaults are written out`() =
        runTest {
            val json = write(StoredUserData())

            assertThat(json).contains("\"themeMode\":\"System\"")
            assertThat(json).contains("\"recentSearches\":[]")
        }

    @Test
    fun `missing fields take their defaults`() =
        runTest {
            assertThat(read("{}")).isEqualTo(StoredUserData())
            assertThat(read("""{"preferences":{"themeMode":"Light"}}""").preferences)
                .isEqualTo(UserPreferences(themeMode = ThemeMode.Light))
        }

    @Test
    fun `unknown keys are ignored`() =
        runTest {
            val data = read("""{"preferences":{"themeMode":"Dark","fontFamily":"Comic"},"newFeature":1}""")

            assertThat(data.preferences.themeMode).isEqualTo(ThemeMode.Dark)
        }

    @Test
    fun `an unknown enum value falls back to the default`() =
        runTest {
            val data = read("""{"preferences":{"themeMode":"Sepia","preferredEdition":"Bedrock"}}""")

            assertThat(data.preferences.themeMode).isEqualTo(ThemeMode.System)
            assertThat(data.preferences.preferredEdition).isEqualTo(Edition.Bedrock)
        }

    @Test
    fun `malformed content is reported as corruption`() =
        runTest {
            listOf("", "not json", "{\"preferences\":", "[1,2]").forEach { content ->
                assertThrows(CorruptionException::class.java) { runBlocking { read(content) } }
            }
        }

    @Test
    fun `default value is exposed for a missing file`() {
        assertThat(serializer.defaultValue).isEqualTo(StoredUserData())
    }
}
