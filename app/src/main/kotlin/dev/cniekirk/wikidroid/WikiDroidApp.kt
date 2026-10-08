package dev.cniekirk.wikidroid

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dev.zacsweers.metro.createGraphFactory
import okhttp3.HttpUrl.Companion.toHttpUrl

class WikiDroidApp :
    Application(),
    SingletonImageLoader.Factory {
    val graph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(
            application = this,
            baseUrl = WIKI_BASE_URL.toHttpUrl(),
        )
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = graph.imageLoader

    private companion object {
        const val WIKI_BASE_URL = "https://minecraft.wiki/"
    }
}
