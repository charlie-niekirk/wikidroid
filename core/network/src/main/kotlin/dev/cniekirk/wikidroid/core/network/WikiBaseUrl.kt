package dev.cniekirk.wikidroid.core.network

import dev.zacsweers.metro.Qualifier

/**
 * The wiki's root URL (`https://minecraft.wiki/`). The app graph's factory binds it, so tests and the
 * instrumented suite can point the whole stack at a MockWebServer.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class WikiBaseUrl
