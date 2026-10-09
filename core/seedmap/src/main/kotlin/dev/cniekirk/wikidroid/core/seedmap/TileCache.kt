package dev.cniekirk.wikidroid.core.seedmap

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Rendered tiles, most recently used kept, within [maxBytes] of bitmap memory.
 *
 * [peek] answers at once from memory, for drawing whatever is ready; [load] fills a gap. Two callers that
 * ask for the same missing tile at the same moment both generate it, which wastes work but is correct:
 * callers choose which tiles to ask for.
 */
class TileCache(
    private val engine: SeedMapEngine,
    private val renderer: TileRenderer,
    maxBytes: Int,
) {
    private val bitmaps =
        object : LruCache<TileKey, Bitmap>(maxBytes) {
            override fun sizeOf(
                key: TileKey,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }

    /** The tile if it is already rendered. */
    fun peek(key: TileKey): Bitmap? = bitmaps[key]

    /** The tile, generated and rendered if it was not cached. */
    suspend fun load(key: TileKey): Bitmap {
        bitmaps[key]?.let { return it }
        val bitmap = renderer.render(engine.biomeTile(key))
        bitmaps.put(key, bitmap)
        return bitmap
    }

    fun clear() = bitmaps.evictAll()
}
