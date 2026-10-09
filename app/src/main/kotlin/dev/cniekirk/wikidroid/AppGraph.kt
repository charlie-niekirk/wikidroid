package dev.cniekirk.wikidroid

import android.app.Application
import coil3.ImageLoader
import dev.cniekirk.wikidroid.core.data.ArticleRepository
import dev.cniekirk.wikidroid.core.data.CategoryRepository
import dev.cniekirk.wikidroid.core.data.LibraryRepository
import dev.cniekirk.wikidroid.core.data.SearchRepository
import dev.cniekirk.wikidroid.core.data.SeedRepository
import dev.cniekirk.wikidroid.core.data.SettingsRepository
import dev.cniekirk.wikidroid.core.data.WikiInfoRepository
import dev.cniekirk.wikidroid.core.navigation.EntryProviderInstaller
import dev.cniekirk.wikidroid.core.network.WikiBaseUrl
import dev.cniekirk.wikidroid.core.seedmap.TileCache
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import okhttp3.HttpUrl

/**
 * The application's dependency graph. Every `@ContributesTo`/`@ContributesBinding` in a module that `:app`
 * depends on directly (see `checkMainMetroHiddenDependencies`) ends up here.
 *
 * Tests build it with a different [WikiBaseUrl], such as a `MockWebServer`'s.
 */
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {
    /** One installer per feature module; empty until the features exist. */
    @Multibinds(allowEmpty = true)
    val entryInstallers: Set<EntryProviderInstaller>

    /** Registered as Coil's singleton loader so every `AsyncImage` shares the API's `OkHttpClient`. */
    val imageLoader: ImageLoader

    // Metro only checks bindings reachable from the graph's roots. Exposing the repositories makes the whole
    // data stack (network, Room, DataStore, parser) resolve at compile time, before any feature injects them.
    val articleRepository: ArticleRepository
    val categoryRepository: CategoryRepository
    val libraryRepository: LibraryRepository
    val searchRepository: SearchRepository
    val seedRepository: SeedRepository
    val settingsRepository: SettingsRepository
    val wikiInfoRepository: WikiInfoRepository

    /** Resolves the seed map engine and renderer too, and the `Application` the cache sizes itself from. */
    val tileCache: TileCache

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
            @Provides @WikiBaseUrl baseUrl: HttpUrl,
        ): AppGraph
    }
}
