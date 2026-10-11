package dev.cniekirk.wikidroid.core.seedmap

import androidx.compose.runtime.Immutable

/** One row of the legend's biome list: the biome, the wiki article it opens and the colour the map paints it. */
@Immutable
data class BiomeLegendEntry(
    val id: Int,
    val title: String,
    /** `0xAARRGGBB`, as in the tiles. */
    val argb: Int,
)
