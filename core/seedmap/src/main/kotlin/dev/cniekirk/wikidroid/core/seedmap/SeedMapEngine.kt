package dev.cniekirk.wikidroid.core.seedmap

/**
 * Asks the world generator what a seed looks like. Calls are safe from any coroutine; they run off the
 * main thread, and a native generator is never used by two calls at once.
 *
 * Inputs are validated before they reach native code, which would crash the process on a bad value:
 * an unsupported combination throws [IllegalArgumentException]. Native work cannot be interrupted, so
 * cancelling a call stops it from starting or from delivering a result, not mid-flight; the calls are
 * small (one tile, one region) so that costs milliseconds.
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

    /** The biome ids of one [TILE_CELLS] x [TILE_CELLS] tile. */
    suspend fun biomeTile(key: TileKey): BiomeTile

    /**
     * Where structures of [type] sit inside [area], in no particular order. A position is the block where
     * the game attempts to generate the structure, which is near its corner and not its centre, so ask for
     * a little more than is on screen. [type] must be region based and exist in [world]'s version and
     * dimension, and [area] at most [MAX_STRUCTURE_SPAN] blocks across.
     */
    suspend fun structuresIn(
        world: MapWorld,
        type: StructureType,
        area: BlockArea,
    ): List<StructurePos>

    /** The first [count] strongholds, nearest ring first. Empty in versions without strongholds. */
    suspend fun strongholds(
        world: MapWorld,
        count: Int = MAX_STRONGHOLDS,
    ): List<BlockPos>

    /** Cubiomes' biome colour table: 256 opaque `0xAARRGGBB` values indexed by biome id. */
    suspend fun biomeColors(): IntArray

    companion object {
        const val SEA_LEVEL = 63

        /** The farthest block from the origin the game lets you reach. */
        const val MAX_COORDINATE = 30_000_000
        const val MIN_Y = -64
        const val MAX_Y = 320

        /** The widest side of an [area][BlockArea] [structuresIn] accepts. */
        const val MAX_STRUCTURE_SPAN = 8192

        /** How many strongholds a world has (from 1.9; earlier versions have 3). */
        const val MAX_STRONGHOLDS = 128
    }
}
