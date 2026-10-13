package dev.cniekirk.wikidroid.core.seedmap

/**
 * Which chunks of the Overworld can spawn slimes below Y 40, whatever the biome. It depends on the seed alone and
 * has not changed between versions, so it is worked out here rather than in native code.
 *
 * This is cubiomes' `isSlimeChunk` (`finders.h`): the game's `new Random(...).nextInt(10) == 0` with Java's
 * random number generator written out, so a region of a thousand chunks allocates nothing. The integer products
 * overflow exactly as Java's (and `-fwrapv` C's) do; `SlimeChunksTest` holds values from a host build of cubiomes.
 */
object SlimeChunks {
    /** Chunks along one side of a region file (512 blocks), which is what [region] answers for. */
    const val REGION_CHUNKS = 32

    fun isSlimeChunk(
        seed: Long,
        chunkX: Int,
        chunkZ: Int,
    ): Boolean {
        val mixed =
            (
                seed +
                    (chunkX * 0x5ac0db).toLong() +
                    (chunkX * chunkX * 0x4c1906).toLong() +
                    (chunkZ * 0x5f24f).toLong() +
                    (chunkZ * chunkZ).toLong() * 0x4307a7L
            ) xor 0x3ad8025fL
        var state = (mixed xor MULTIPLIER) and MASK
        // java.util.Random.nextInt(10): draw 31 bits, retrying the few draws that would bias the result.
        while (true) {
            state = (state * MULTIPLIER + ADDEND) and MASK
            val bits = (state ushr BITS_SHIFT).toInt()
            val value = bits % SLIME_ODDS
            if (bits - value + (SLIME_ODDS - 1) >= 0) return value == 0
        }
    }

    /**
     * The slime chunks of the region at [regionX], [regionZ] (in 512-block steps), row by row:
     * `[dz * REGION_CHUNKS + dx]` is the chunk [dx] east and [dz] south of its north-west corner.
     */
    fun region(
        seed: Long,
        regionX: Int,
        regionZ: Int,
    ): BooleanArray {
        val firstX = regionX * REGION_CHUNKS
        val firstZ = regionZ * REGION_CHUNKS
        return BooleanArray(REGION_CHUNKS * REGION_CHUNKS) { i ->
            isSlimeChunk(seed, firstX + i % REGION_CHUNKS, firstZ + i / REGION_CHUNKS)
        }
    }

    private const val MULTIPLIER = 0x5DEECE66DL
    private const val ADDEND = 0xBL
    private const val MASK = (1L shl 48) - 1
    private const val BITS_SHIFT = 17
    private const val SLIME_ODDS = 10
}
