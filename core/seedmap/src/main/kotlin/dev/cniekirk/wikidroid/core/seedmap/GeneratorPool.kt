package dev.cniekirk.wikidroid.core.seedmap

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

/**
 * A fixed number of native generators, one per worker. Every cubiomes query mutates its generator, so a
 * generator is lent to one caller at a time and queries for different tiles run side by side.
 *
 * A generator is created, and pointed at a seed, only when a caller needs it: the first request for a
 * world pays for setting one up, later requests for the same world find one ready, and a request for
 * another seed, version or dimension re-seeds (or, for another version, re-creates) whichever generator
 * is free. Prefer a generator that is already on the requested world.
 *
 * [use] runs its block on [dispatcher], limited to [size] threads, so the native library is first
 * loaded off the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class GeneratorPool(
    dispatcher: CoroutineDispatcher,
    val size: Int = defaultSize(),
) : AutoCloseable {
    init {
        require(size >= 1) { "A pool needs at least one generator" }
    }

    private val workers = dispatcher.limitedParallelism(size)
    private val permits = Semaphore(size)
    private val lock = Any()
    private val idle = ArrayList<Slot>(size)
    private val all = ArrayList<Slot>(size)

    @Volatile private var closed = false

    /** Lends a generator that is set up for [world] to [block], which must not keep the handle. */
    suspend fun <T> use(
        world: MapWorld,
        block: (handle: Long) -> T,
    ): T {
        check(!closed) { "The generator pool is closed" }
        permits.acquire()
        try {
            check(!closed) { "The generator pool is closed" }
            return withContext(workers) {
                ensureActive()
                val slot = take(world)
                try {
                    block(slot.prepare(world))
                } finally {
                    give(slot)
                }
            }
        } finally {
            permits.release()
        }
    }

    /** Waits for lent generators to come back, then frees all of them. Later calls to [use] fail. */
    suspend fun shutDown() {
        closed = true
        repeat(size) { permits.acquire() }
        try {
            destroyAll()
        } finally {
            // Callers already waiting for a permit wake up, see `closed`, and fail instead of hanging.
            repeat(size) { permits.release() }
        }
    }

    /** Frees every generator. The caller must know none is lent: use [shutDown] if unsure. */
    override fun close() {
        closed = true
        destroyAll()
    }

    private fun destroyAll() {
        synchronized(lock) {
            all.forEach { it.destroy() }
            all.clear()
            idle.clear()
        }
    }

    /** The semaphore guarantees that an idle slot exists or the pool has room for a new one. */
    private fun take(world: MapWorld): Slot =
        synchronized(lock) {
            val slot =
                idle.firstOrNull { it.world == world }
                    ?: idle.firstOrNull { it.version == world.version }
                    ?: idle.firstOrNull()
                    ?: Slot().also { all += it }
            idle.remove(slot)
            slot
        }

    private fun give(slot: Slot) {
        synchronized(lock) {
            if (slot in all) idle += slot else slot.destroy()
        }
    }

    private class Slot {
        private var handle = 0L
        var version: McVersion? = null
            private set
        var world: MapWorld? = null
            private set

        /** The native handle, created and seeded for [target] if it is not already. */
        fun prepare(target: MapWorld): Long {
            if (world == target) return handle
            // Forget the old world first: if seeding fails below, the slot must not claim to still match it.
            world = null
            if (handle != 0L && version != target.version) destroy()
            if (handle == 0L) {
                handle = NativeSeedMap.nativeCreate(target.version.nativeId, NO_FLAGS)
                check(handle != 0L) { "Native generator for ${target.version.label} could not be created" }
                version = target.version
            }
            check(NativeSeedMap.nativeApplySeed(handle, target.dimension.nativeId, target.seed)) {
                // MapWorld rules this combination out, so this is a mismatch with the native table.
                "Native refused ${target.dimension} for ${target.version.label}"
            }
            world = target
            return handle
        }

        fun destroy() {
            if (handle != 0L) NativeSeedMap.nativeDestroy(handle)
            handle = 0L
            version = null
            world = null
        }
    }

    companion object {
        private const val NO_FLAGS = 0

        /** One fewer than the cores, so tile work leaves one for the UI; at least one. */
        fun defaultSize(): Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)
    }
}
