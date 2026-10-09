package dev.cniekirk.wikidroid.core.seedmap

import android.graphics.Bitmap
import dev.zacsweers.metro.Inject

/**
 * Colours a [BiomeTile]. The palette comes from the engine the first time a tile is drawn, so building
 * a renderer never loads the native library.
 */
@Inject
class TileRenderer(
    private val engine: SeedMapEngine,
) {
    @Volatile private var palette: BiomePalette? = null

    /** A [TILE_CELLS] x [TILE_CELLS] bitmap with one pixel per cell, north-west corner at the top left. */
    suspend fun render(tile: BiomeTile): Bitmap {
        val pixels = palette().paint(tile.biomes)
        return Bitmap.createBitmap(pixels, TILE_CELLS, TILE_CELLS, Bitmap.Config.ARGB_8888)
    }

    private suspend fun palette(): BiomePalette = palette ?: BiomePalette(engine.biomeColors()).also { palette = it }
}
