package dev.cniekirk.wikidroid.core.ui.di

import android.app.Application
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okhttp3.OkHttpClient

/**
 * The app's Coil `ImageLoader`. Image requests go through the same [OkHttpClient] as the API (bound in
 * `:core:network`), so they share its User-Agent, HTTP cache and connection pool. The application registers
 * this loader with `SingletonImageLoader` so `PageThumbnail` and the article images pick it up.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object ImageLoaderProviders {
    @Provides
    @SingleIn(AppScope::class)
    fun provideImageLoader(
        application: Application,
        okHttpClient: OkHttpClient,
    ): ImageLoader =
        ImageLoader
            .Builder(application)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
            .build()
}
