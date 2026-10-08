package dev.cniekirk.wikidroid

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dev.zacsweers.metro.createGraphFactory
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** `open` so the instrumented tests' `TestWikiApp` can point the graph at a `MockWebServer`. */
open class WikiDroidApp :
    Application(),
    SingletonImageLoader.Factory {
    val graph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(
            application = this,
            baseUrl = baseUrl(),
        )
    }

    /** The wiki the app talks to. Read once, when the graph is first built. */
    protected open fun baseUrl(): HttpUrl = WIKI_BASE_URL.toHttpUrl()

    override fun newImageLoader(context: PlatformContext): ImageLoader = graph.imageLoader

    private companion object {
        const val WIKI_BASE_URL = "https://minecraft.wiki/"
    }
}
