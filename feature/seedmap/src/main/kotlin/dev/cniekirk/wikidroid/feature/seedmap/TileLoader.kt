package dev.cniekirk.wikidroid.feature.seedmap

import dev.cniekirk.wikidroid.core.seedmap.TileCache
import dev.cniekirk.wikidroid.core.seedmap.TileKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fills the tile cache with the tiles the canvas wants, and only those: each call to [request] cancels
 * the loads for tiles that are no longer wanted (they scrolled out of view or the world changed) and
 * starts the ones that are missing. A tile already loading or failed is not asked for again, which also
 * covers [TileCache.load] not de-duplicating simultaneous loads.
 *
 * [scope] runs the bookkeeping and [workDispatcher] the generation and painting. [onLoaded] runs on
 * [scope] after each tile lands in the cache. The bookkeeping is guarded by a lock because a cancelled
 * job tidies up on whichever thread cancels it (inline, on an immediate dispatcher), and Compose may
 * dispose the canvas from a different thread than the one that last asked for tiles.
 */
internal class TileLoader(
    private val scope: CoroutineScope,
    private val cache: TileCache,
    private val workDispatcher: CoroutineDispatcher,
    private val onLoaded: () -> Unit,
) {
    private val lock = Any()
    private val loading = HashMap<TileKey, Job>()
    private val failed = HashSet<TileKey>()

    /** The tiles loading right now. */
    val pending: Set<TileKey> get() = synchronized(lock) { loading.keys.toSet() }

    /** Loads the tiles of [wanted] the cache does not have, in the order given (put the nearest first). */
    fun request(wanted: List<TileKey>) {
        val keep = wanted.toSet()
        val unwanted =
            synchronized(lock) {
                loading.keys
                    .filterNot { it in keep }
                    .mapNotNull { loading.remove(it) }
            }
        unwanted.forEach { it.cancel() }
        for (key in wanted) {
            if (cache.peek(key) == null) start(key)
        }
    }

    fun cancelAll() {
        val jobs =
            synchronized(lock) {
                loading.values.toList().also { loading.clear() }
            }
        jobs.forEach { it.cancel() }
    }

    private fun start(key: TileKey) {
        synchronized(lock) {
            if (key !in loading && key !in failed) loading[key] = scope.launch { load(key) }
        }
    }

    private suspend fun load(key: TileKey) {
        val self = currentCoroutineContext().job
        try {
            withContext(workDispatcher) { cache.load(key) }
            onLoaded()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            // The engine refused the tile (past the world border): leave a hole instead of retrying forever.
            synchronized(lock) { failed += key }
        } catch (_: IllegalStateException) {
            synchronized(lock) { failed += key }
        } finally {
            synchronized(lock) { if (loading[key] === self) loading.remove(key) }
        }
    }
}
