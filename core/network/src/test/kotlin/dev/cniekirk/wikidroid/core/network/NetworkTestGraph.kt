package dev.cniekirk.wikidroid.core.network

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * Builds the real network stack through Metro, the way the app graph will, so the tests cover the
 * providers and the `@WikiBaseUrl` binding as well as the endpoints.
 */
@DependencyGraph(AppScope::class)
interface NetworkTestGraph {
    val dataSource: WikiRemoteDataSource
    val okHttpClient: OkHttpClient

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
            @Provides @WikiBaseUrl baseUrl: HttpUrl,
        ): NetworkTestGraph
    }
}
