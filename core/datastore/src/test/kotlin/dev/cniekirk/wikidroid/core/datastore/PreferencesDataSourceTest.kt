package dev.cniekirk.wikidroid.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.Edition
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.model.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesDataSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, "user_data.json") }

    /** DataStore allows one active instance per file; [job] ends it so a "restarted" one can open the file. */
    private fun TestScope.dataStore(job: Job = Job(backgroundScope.coroutineContext[Job])): DataStore<StoredUserData> =
        DataStoreFactory.create(
            serializer = JsonSerializer(StoredUserData.serializer(), StoredUserData()),
            corruptionHandler = ReplaceFileCorruptionHandler { StoredUserData() },
            scope = CoroutineScope(job + UnconfinedTestDispatcher(testScheduler)),
            produceFile = { file },
        )

    private fun TestScope.dataSource() = DataStorePreferencesDataSource(dataStore())

    @Test
    fun `a fresh install has default preferences and no searches`() =
        runTest {
            val source = dataSource()

            assertThat(source.userPreferences.first()).isEqualTo(UserPreferences())
            assertThat(source.recentSearches.first()).isEmpty()
        }

    @Test
    fun `updatePreferences changes only what the transform changes`() =
        runTest {
            val source = dataSource()

            source.updatePreferences { it.copy(themeMode = ThemeMode.Dark, preferredEdition = Edition.Bedrock) }

            assertThat(source.userPreferences.first())
                .isEqualTo(UserPreferences(themeMode = ThemeMode.Dark, preferredEdition = Edition.Bedrock))
        }

    @Test
    fun `preferences are written to disk as JSON`() =
        runTest {
            dataSource().updatePreferences { it.copy(dynamicColor = false) }

            assertThat(file.readText()).contains("\"dynamicColor\":false")
        }

    @Test
    fun `a second data source on the same file sees what the first wrote`() =
        runTest {
            val firstRun = Job(backgroundScope.coroutineContext[Job])
            val first = DataStorePreferencesDataSource(dataStore(firstRun))
            first.updatePreferences { it.copy(themeMode = ThemeMode.Light, textScale = 1.3f) }
            first.addRecentSearch("creeper")

            // Stopping the first store and opening a new one on the file stands in for an app restart.
            firstRun.cancel()
            val restarted = DataStorePreferencesDataSource(dataStore())

            assertThat(restarted.userPreferences.first())
                .isEqualTo(UserPreferences(themeMode = ThemeMode.Light, textScale = 1.3f))
            assertThat(restarted.recentSearches.first()).containsExactly("creeper")
        }

    @Test
    fun `text scale is kept within the supported range`() =
        runTest {
            val source = dataSource()

            source.updatePreferences { it.copy(textScale = 9f) }
            assertThat(source.userPreferences.first().textScale).isEqualTo(UserPreferences.MAX_TEXT_SCALE)

            source.updatePreferences { it.copy(textScale = 0.1f) }
            assertThat(source.userPreferences.first().textScale).isEqualTo(UserPreferences.MIN_TEXT_SCALE)
        }

    @Test
    fun `an unreadable file falls back to defaults and can be written again`() =
        runTest {
            file.writeText("{ this is not json")
            val source = dataSource()

            assertThat(source.userPreferences.first()).isEqualTo(UserPreferences())

            source.updatePreferences { it.copy(themeMode = ThemeMode.Dark) }
            assertThat(source.userPreferences.first().themeMode).isEqualTo(ThemeMode.Dark)
        }

    @Test
    fun `recent searches are listed most recent first`() =
        runTest {
            val source = dataSource()

            listOf("creeper", "zombie", "diamond").forEach { source.addRecentSearch(it) }

            assertThat(source.recentSearches.first()).containsExactly("diamond", "zombie", "creeper").inOrder()
        }

    @Test
    fun `searching again moves the entry to the front ignoring case`() =
        runTest {
            val source = dataSource()
            listOf("creeper", "zombie").forEach { source.addRecentSearch(it) }

            source.addRecentSearch("  Creeper ")

            assertThat(source.recentSearches.first()).containsExactly("Creeper", "zombie").inOrder()
        }

    @Test
    fun `blank searches are ignored`() =
        runTest {
            val source = dataSource()

            source.addRecentSearch("   ")
            source.addRecentSearch("")

            assertThat(source.recentSearches.first()).isEmpty()
        }

    @Test
    fun `only the newest searches are kept`() =
        runTest {
            val source = dataSource()

            (1..15).forEach { source.addRecentSearch("query $it") }

            val searches = source.recentSearches.first()
            assertThat(searches).hasSize(PreferencesDataSource.MAX_RECENT_SEARCHES)
            assertThat(searches.first()).isEqualTo("query 15")
            assertThat(searches.last()).isEqualTo("query 6")
        }

    @Test
    fun `a single search can be removed and all can be cleared`() =
        runTest {
            val source = dataSource()
            listOf("creeper", "zombie", "diamond").forEach { source.addRecentSearch(it) }

            source.removeRecentSearch("ZOMBIE")
            assertThat(source.recentSearches.first()).containsExactly("diamond", "creeper").inOrder()

            source.clearRecentSearches()
            assertThat(source.recentSearches.first()).isEmpty()
        }

    @Test
    fun `searches and preferences do not overwrite each other`() =
        runTest {
            val source = dataSource()

            source.addRecentSearch("creeper")
            source.updatePreferences { it.copy(saveHistory = false) }
            source.addRecentSearch("zombie")

            assertThat(source.userPreferences.first().saveHistory).isFalse()
            assertThat(source.recentSearches.first()).containsExactly("zombie", "creeper").inOrder()
            source.clearRecentSearches()
            assertThat(source.userPreferences.first().saveHistory).isFalse()
        }
}
