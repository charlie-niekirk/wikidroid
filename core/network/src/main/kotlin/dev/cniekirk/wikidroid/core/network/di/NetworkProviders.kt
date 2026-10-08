package dev.cniekirk.wikidroid.core.network.di

import android.app.Application
import android.content.pm.ApplicationInfo
import dev.cniekirk.wikidroid.core.network.DefaultParamsInterceptor
import dev.cniekirk.wikidroid.core.network.MediaWikiApi
import dev.cniekirk.wikidroid.core.network.UserAgentInterceptor
import dev.cniekirk.wikidroid.core.network.WikiBaseUrl
import dev.cniekirk.wikidroid.core.network.WikiRestApi
import dev.cniekirk.wikidroid.core.network.wikiUserAgent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.io.File
import java.util.concurrent.TimeUnit

private const val CACHE_SIZE_BYTES = 50L * 1024 * 1024
private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 30L
private const val UNKNOWN_VERSION = "unknown"

/**
 * Requires `Application` and `@WikiBaseUrl HttpUrl` from the graph's factory. The [OkHttpClient] is
 * public in the graph so the Coil `ImageLoader` can share its User-Agent, cache and connection pool.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object NetworkProviders {
    @Provides
    @SingleIn(AppScope::class)
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Provides
    @SingleIn(AppScope::class)
    fun provideOkHttpClient(application: Application): OkHttpClient {
        val versionName =
            runCatching { application.packageManager.getPackageInfo(application.packageName, 0).versionName }
                .getOrNull() ?: UNKNOWN_VERSION
        val debuggable = application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        return OkHttpClient
            .Builder()
            .cache(Cache(File(application.cacheDir, "http"), CACHE_SIZE_BYTES))
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor(wikiUserAgent(versionName)))
            .addInterceptor(DefaultParamsInterceptor)
            .apply {
                // BASIC: article responses are hundreds of KB, so bodies are never logged.
                if (debuggable) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }.build()
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
        @WikiBaseUrl baseUrl: HttpUrl,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()

    @Provides
    @SingleIn(AppScope::class)
    fun provideMediaWikiApi(retrofit: Retrofit): MediaWikiApi = retrofit.create()

    @Provides
    @SingleIn(AppScope::class)
    fun provideWikiRestApi(retrofit: Retrofit): WikiRestApi = retrofit.create()
}
