package dev.cniekirk.wikidroid.core.datastore

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.testing.RobolectricTest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

/** Builds the data source through the real Metro providers, as the app graph will. */
@DependencyGraph(AppScope::class)
interface DataStoreTestGraph {
    val preferences: PreferencesDataSource

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
        ): DataStoreTestGraph
    }
}

class DataStoreProvidersTest : RobolectricTest() {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val graph = createGraphFactory<DataStoreTestGraph.Factory>().create(application)

    @Test
    fun `the graph provides a working data source backed by a file in app storage`() =
        runTest {
            graph.preferences.updatePreferences { it.copy(themeMode = ThemeMode.Dark) }
            graph.preferences.addRecentSearch("creeper")

            assertThat(
                graph.preferences.userPreferences
                    .first()
                    .themeMode,
            ).isEqualTo(ThemeMode.Dark)
            assertThat(graph.preferences.recentSearches.first()).containsExactly("creeper")
            assertThat(File(application.filesDir, "datastore/user_data.json").exists()).isTrue()
        }

    @Test
    fun `the data source is a singleton`() {
        assertThat(graph.preferences).isSameInstanceAs(graph.preferences)
    }
}
