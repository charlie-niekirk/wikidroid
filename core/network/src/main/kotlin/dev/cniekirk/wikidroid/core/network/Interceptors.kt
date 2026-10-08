package dev.cniekirk.wikidroid.core.network

import okhttp3.Interceptor
import okhttp3.Response

/** minecraft.wiki answers an empty or generic User-Agent with 403. */
internal fun wikiUserAgent(versionName: String): String =
    "WikiDroid/$versionName (Android; https://github.com/charlie-niekirk/wikidroid)"

internal class UserAgentInterceptor(
    private val userAgent: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain
                .request()
                .newBuilder()
                .header("User-Agent", userAgent)
                .build(),
        )
}

/** Adds `format=json&formatversion=2` to `api.php` calls so the Retrofit interface doesn't repeat them. */
internal object DefaultParamsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (!url.encodedPath.endsWith("/api.php")) return chain.proceed(request)

        val withDefaults =
            url
                .newBuilder()
                .apply {
                    if (url.queryParameter("format") == null) addQueryParameter("format", "json")
                    if (url.queryParameter("formatversion") == null) addQueryParameter("formatversion", "2")
                }.build()
        return chain.proceed(request.newBuilder().url(withDefaults).build())
    }
}
