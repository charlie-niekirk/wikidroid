package dev.cniekirk.wikidroid.core.seedmap

/**
 * The JNI surface of `libseedmap.so`. Everything here is a primitive or a primitive array; the C side
 * trusts nothing and the Kotlin side ([JniSeedMapEngine]) validates before calling.
 *
 * `libseedmap.so` is loaded the first time this object is touched, never at app start, and never in
 * JVM or Robolectric tests, which only ever see [SeedMapEngine] fakes.
 */
@Suppress("TooManyFunctions") // One declaration per native function; splitting would only move them.
internal object NativeSeedMap {
    init {
        System.loadLibrary("seedmap")
    }

    /** A generator handle for [version] (a cubiomes `MCVersion` int), or 0 on failure. Free it with [nativeDestroy]. */
    @JvmStatic external fun nativeCreate(
        version: Int,
        flags: Int,
    ): Long

    @JvmStatic external fun nativeDestroy(handle: Long)

    /** Returns false, changing nothing, if [dimension] does not exist in the generator's version. */
    @JvmStatic external fun nativeApplySeed(
        handle: Long,
        dimension: Int,
        seed: Long,
    ): Boolean

    /** The biome id, or -1 if the generator has no seed yet or [scale] is not 1 or 4. */
    @JvmStatic external fun nativeGetBiomeAt(
        handle: Long,
        scale: Int,
        x: Int,
        y: Int,
        z: Int,
    ): Int

    /** `[x, z]`, or null unless the generator is on the overworld. */
    @JvmStatic external fun nativeGetSpawn(handle: Long): IntArray?

    /**
     * Biome ids for a [width] x [height] area, row by row, whose north-west cell is ([x], [z]) in units of
     * [scale] blocks (1, 4, 16, 64 or 256). [y] is in blocks at scale 1 and in 4-block units otherwise.
     * Null if the arguments are invalid or the generator has no seed.
     */
    @JvmStatic external fun nativeGenBiomes(
        handle: Long,
        scale: Int,
        x: Int,
        z: Int,
        width: Int,
        height: Int,
        y: Int,
    ): IntArray?

    /**
     * Positions of one structure type in `[x0, x1) x [z0, z1)` as `[x, z, x, z, ...]`. Empty when there are
     * none; null if the arguments are invalid or the structure does not exist for the generator's
     * version and dimension.
     */
    @JvmStatic external fun nativeStructures(
        handle: Long,
        type: Int,
        x0: Int,
        z0: Int,
        x1: Int,
        z1: Int,
    ): IntArray?

    /** Up to [count] stronghold positions as `[x, z, ...]`, nearest ring first; null unless on the overworld. */
    @JvmStatic external fun nativeStrongholds(
        handle: Long,
        count: Int,
    ): IntArray?

    /** 256 opaque `0xAARRGGBB` colours indexed by biome id. */
    @JvmStatic external fun nativeBiomeColors(): IntArray

    @JvmStatic external fun nativeStructureSupported(
        type: Int,
        version: Int,
        dimension: Int,
    ): Boolean

    /** cubiomes' name for a structure type, or null if it has none. */
    @JvmStatic external fun nativeStructureName(type: Int): String?

    /** The biome's resource name in [version], or null if the biome does not exist there. */
    @JvmStatic external fun nativeBiomeName(
        version: Int,
        id: Int,
    ): String?

    @JvmStatic external fun nativeVersionName(version: Int): String?

    @JvmStatic external fun nativeNewestVersion(): Int
}
