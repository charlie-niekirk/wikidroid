package dev.cniekirk.wikidroid

import android.app.Application
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.testing.RobolectricTest
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
    fun noFeatureInstallersAreRegisteredYet() {
        assertThat(graph.entryInstallers).isEmpty()
    }

    @Test
    fun theApplicationRegistersTheGraphsImageLoaderWithCoil() {
        val app = application as WikiDroidApp

        assertThat(SingletonImageLoader.get(app)).isSameInstanceAs(app.graph.imageLoader)
    }
}
