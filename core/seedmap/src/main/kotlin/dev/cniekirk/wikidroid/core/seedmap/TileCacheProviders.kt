package dev.cniekirk.wikidroid.core.seedmap

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Requires `Application` from the graph's factory. */
@BindingContainer
@ContributesTo(AppScope::class)
object TileCacheProviders {
    @Provides
    @SingleIn(AppScope::class)
    fun provideTileCache(
        application: Application,
        engine: SeedMapEngine,
        renderer: TileRenderer,
    ): TileCache {
        val memoryClassMb = (application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
        return TileCache(engine, renderer, tileCacheBytes(memoryClassMb))
    }
}

/** An eighth of the app's heap limit, in bytes (at least one tile, so the cache is never useless). */
internal fun tileCacheBytes(memoryClassMb: Int): Int =
    (memoryClassMb.toLong() * BYTES_PER_MB / HEAP_SHARE_DIVISOR)
        .coerceIn(TILE_BYTES.toLong(), Int.MAX_VALUE.toLong())
        .toInt()

private const val BYTES_PER_MB = 1024 * 1024
private const val HEAP_SHARE_DIVISOR = 8
private const val TILE_BYTES = TILE_CELLS * TILE_CELLS * 4
