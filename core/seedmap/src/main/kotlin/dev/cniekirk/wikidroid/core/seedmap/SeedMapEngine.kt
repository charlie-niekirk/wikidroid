package dev.cniekirk.wikidroid.core.seedmap

/**
 * Asks the world generator what a seed looks like. Calls are safe from any coroutine; they run off the
 * main thread and the native generator is never used by two at once.
 *
 * Inputs are validated before they reach native code, which would crash the process on a bad value:
 * an unsupported combination throws [IllegalArgumentException].
 */
interface SeedMapEngine {
    /** The world spawn point. Only the overworld has one. */
    suspend fun spawn(world: MapWorld): BlockPos

    /**
     * The biome id at one block column and height ([SEA_LEVEL] by default), or null if the generator
     * cannot answer. Ids are cubiomes' `BiomeID` values, which follow Minecraft's numeric ids.
     * [x] and [z] must be within the world border, [MAX_COORDINATE].
     */
    suspend fun biomeAt(
        world: MapWorld,
        x: Int,
        z: Int,
        y: Int = SEA_LEVEL,
    ): Int?

    companion object {
        const val SEA_LEVEL = 63

        /** The farthest block from the origin the game lets you reach. */
        const val MAX_COORDINATE = 30_000_000
        const val MIN_Y = -64
        const val MAX_Y = 320
    }
}
