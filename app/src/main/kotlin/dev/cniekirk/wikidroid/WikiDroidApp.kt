package dev.cniekirk.wikidroid

import android.app.Application
import dev.zacsweers.metro.createGraph

class WikiDroidApp : Application() {
    val graph: AppGraph by lazy { createGraph<AppGraph>() }
}
