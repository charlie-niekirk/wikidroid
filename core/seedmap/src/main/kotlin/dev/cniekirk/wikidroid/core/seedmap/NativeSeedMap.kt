package dev.cniekirk.wikidroid.core.seedmap

/**
 * The JNI surface of `libseedmap.so`. Everything here is a primitive or a primitive array; the C side
 * trusts nothing and the Kotlin side ([JniSeedMapEngine]) validates before calling.
 *
 * `libseedmap.so` is loaded the first time this object is touched, never at app start, and never in
 * JVM or Robolectric tests, which only ever see [SeedMapEngine] fakes.
 */
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

    @JvmStatic external fun nativeVersionName(version: Int): String?

    @JvmStatic external fun nativeNewestVersion(): Int
}
