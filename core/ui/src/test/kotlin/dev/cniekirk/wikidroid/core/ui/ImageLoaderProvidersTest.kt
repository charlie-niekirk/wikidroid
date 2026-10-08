package dev.cniekirk.wikidroid.core.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.testing.RobolectricTest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import okhttp3.OkHttpClient
import org.junit.Test

/** Stands in for the app graph, which gets the [OkHttpClient] from `:core:network`. */
@DependencyGraph(AppScope::class)
interface UiTestGraph {
    val imageLoader: ImageLoader

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
            @Provides okHttpClient: OkHttpClient,
        ): UiTestGraph
    }
}

class ImageLoaderProvidersTest : RobolectricTest() {
    private val graph =
        createGraphFactory<UiTestGraph.Factory>().create(
            application = ApplicationProvider.getApplicationContext(),
            okHttpClient = OkHttpClient(),
        )

    @Test
    fun theImageLoaderIsContributedAndIsASingleton() {
        assertThat(graph.imageLoader).isSameInstanceAs(graph.imageLoader)
    }
}
