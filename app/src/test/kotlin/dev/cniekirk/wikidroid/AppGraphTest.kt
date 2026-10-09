package dev.cniekirk.wikidroid

import android.app.Application
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.testing.RobolectricTest
import dev.cniekirk.wikidroid.feature.article.ArticleViewModel
import dev.cniekirk.wikidroid.feature.explore.CategoryDetailViewModel
import dev.cniekirk.wikidroid.feature.explore.ExploreViewModel
import dev.cniekirk.wikidroid.feature.library.LibraryViewModel
import dev.cniekirk.wikidroid.feature.search.SearchViewModel
import dev.cniekirk.wikidroid.feature.seedmap.SeedMapViewModel
import dev.cniekirk.wikidroid.feature.settings.SettingsViewModel
import dev.zacsweers.metro.createGraphFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

/** Builds the real [AppGraph], so a binding that `:app` can't see fails here as well as at compile time. */
class AppGraphTest : RobolectricTest() {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val graph = createGraphFactory<AppGraph.Factory>().create(application, "http://localhost/".toHttpUrl())

    @Test
    fun theViewModelFactoryBuildsTheMainViewModel() {
        val viewModel = graph.metroViewModelFactory.create(MainViewModel::class, CreationExtras.Empty)

        assertThat(viewModel).isInstanceOf(MainViewModel::class.java)
    }

    @Test
    fun theImageLoaderIsASingleton() {
        assertThat(graph.imageLoader).isSameInstanceAs(graph.imageLoader)
    }

    @Test
    fun theFeaturesRegisterTheirScreens() {
        assertThat(graph.entryInstallers.map { it::class.simpleName })
            .containsExactly(
                "ArticleEntryInstaller",
                "ExploreEntryInstaller",
                "LibraryEntryInstaller",
                "SearchEntryInstaller",
                "SeedMapEntryInstaller",
                "SettingsEntryInstaller",
            )
    }

    @Test
    fun theViewModelFactoryBuildsTheFeatureViewModels() {
        val factory = graph.metroViewModelFactory

        assertThat(
            factory.create(ExploreViewModel::class, CreationExtras.Empty),
        ).isInstanceOf(ExploreViewModel::class.java)
        assertThat(
            factory.create(SearchViewModel::class, CreationExtras.Empty),
        ).isInstanceOf(SearchViewModel::class.java)
        assertThat(
            factory.create(LibraryViewModel::class, CreationExtras.Empty),
        ).isInstanceOf(LibraryViewModel::class.java)
        assertThat(
            factory.create(SettingsViewModel::class, CreationExtras.Empty),
        ).isInstanceOf(SettingsViewModel::class.java)
        assertThat(
            factory.create(SeedMapViewModel::class, CreationExtras.Empty),
        ).isInstanceOf(SeedMapViewModel::class.java)
    }

    @Test
    fun theCategoryViewModelIsBuiltFromItsAssistedFactory() {
        val factory = graph.metroViewModelFactory.createManuallyAssistedFactory(CategoryDetailViewModel.Factory::class)

        val viewModel = factory().create("Hostile mobs")

        assertThat(viewModel.container.stateFlow.value.title).isEqualTo("Hostile mobs")
    }

    @Test
    fun theArticleViewModelIsBuiltFromItsAssistedFactory() {
        val factory = graph.metroViewModelFactory.createManuallyAssistedFactory(ArticleViewModel.Factory::class)

        val viewModel = factory().create("Diamond", "Uses")

        assertThat(viewModel.container.stateFlow.value.title).isEqualTo("Diamond")
    }

    @Test
    fun theApplicationRegistersTheGraphsImageLoaderWithCoil() {
        val app = application as WikiDroidApp

        assertThat(SingletonImageLoader.get(app)).isSameInstanceAs(app.graph.imageLoader)
    }
}
