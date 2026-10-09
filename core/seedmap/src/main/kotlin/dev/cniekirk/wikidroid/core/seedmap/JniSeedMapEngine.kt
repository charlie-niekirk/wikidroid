package dev.cniekirk.wikidroid.core.seedmap

import dev.cniekirk.wikidroid.core.common.DefaultDispatcher
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One native generator behind a lock. Enough for single-point queries; the tile work in the next session
 * replaces the lock with a pool of generators.
 *
 * The native library is loaded on the first call, so building the graph costs nothing.
 */
@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class JniSeedMapEngine(
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : SeedMapEngine {
    private val lock = Mutex()
    private var current: Seeded? = null

    override suspend fun spawn(world: MapWorld): BlockPos {
        require(world.dimension == Dimension.OVERWORLD) { "Only the overworld has a spawn point" }
        return withGenerator(world) { handle ->
            val spawn = checkNotNull(NativeSeedMap.nativeGetSpawn(handle)) { "Native spawn lookup failed" }
            BlockPos(spawn[0], spawn[1])
        }
    }

    override suspend fun biomeAt(
        world: MapWorld,
        x: Int,
        z: Int,
        y: Int,
    ): Int? {
        val limit = SeedMapEngine.MAX_COORDINATE
        require(x in -limit..limit && z in -limit..limit) { "($x, $z) is outside the world border" }
        require(y in SeedMapEngine.MIN_Y..SeedMapEngine.MAX_Y) { "Height $y is outside the world" }
        return withGenerator(world) { handle ->
            NativeSeedMap.nativeGetBiomeAt(handle, BLOCK_SCALE, x, y, z).takeIf { it != NO_BIOME }
        }
    }

    private suspend fun <T> withGenerator(
        world: MapWorld,
        block: (handle: Long) -> T,
    ): T =
        withContext(dispatcher) {
            lock.withLock {
                block(seeded(world))
            }
        }

    /** The generator for [world], created or re-seeded only when it changed. Call with [lock] held. */
    private fun seeded(world: MapWorld): Long {
        val existing = current
        if (existing != null && existing.world == world) return existing.handle

        val reusable = existing?.takeIf { it.world.version == world.version }
        val handle = reusable?.handle ?: create(world.version, replacing = existing)
        if (!NativeSeedMap.nativeApplySeed(handle, world.dimension.nativeId, world.seed)) {
            // MapWorld rules this combination out, so this is a mismatch with the native table.
            if (reusable == null) NativeSeedMap.nativeDestroy(handle)
            error("Native refused ${world.dimension} for ${world.version.label}")
        }
        current = Seeded(handle, world)
        return handle
    }

    private fun create(
        version: McVersion,
        replacing: Seeded?,
    ): Long {
        replacing?.let { NativeSeedMap.nativeDestroy(it.handle) }
        current = null
        val handle = NativeSeedMap.nativeCreate(version.nativeId, NO_FLAGS)
        check(handle != 0L) { "Native generator for ${version.label} could not be created" }
        return handle
    }

    private class Seeded(
        val handle: Long,
        val world: MapWorld,
    )

    private companion object {
        const val BLOCK_SCALE = 1
        const val NO_BIOME = -1
        const val NO_FLAGS = 0
    }
}
