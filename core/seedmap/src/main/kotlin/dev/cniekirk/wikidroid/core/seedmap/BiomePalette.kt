package dev.cniekirk.wikidroid.core.seedmap

/** Turns biome ids into pixel colours. [colors] is indexed by biome id, as `0xAARRGGBB`. */
class BiomePalette(
    colors: IntArray,
) {
    private val colors = colors.copyOf()

    /** The colour of [biome], or [UNKNOWN] for an id outside the table. */
    fun argb(biome: Int): Int = if (biome in colors.indices) colors[biome] else UNKNOWN

    /** One colour per cell of [biomes], in the same order. */
    fun paint(biomes: IntArray): IntArray = IntArray(biomes.size) { argb(biomes[it]) }

    companion object {
        const val UNKNOWN = 0xFF000000.toInt()
    }
}
