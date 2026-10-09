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
 * Meant for the main thread: [scope] runs the bookkeeping and [workDispatcher] the generation and
 * painting. [onLoaded] runs on [scope] after each tile lands in the cache.
 */
internal class TileLoader(
    private val scope: CoroutineScope,
    private val cache: TileCache,
    private val workDispatcher: CoroutineDispatcher,
    private val onLoaded: () -> Unit,
) {
    private val loading = HashMap<TileKey, Job>()
    private val failed = HashSet<TileKey>()

    /** The tiles loading right now. */
    val pending: Set<TileKey> get() = loading.keys

    /** Loads the tiles of [wanted] the cache does not have, in the order given (put the nearest first). */
    fun request(wanted: List<TileKey>) {
        val keep = wanted.toSet()
        loading.keys
            .filterNot { it in keep }
            .forEach { loading.remove(it)?.cancel() }
        for (key in wanted) {
            if (key in loading || key in failed || cache.peek(key) != null) continue
            loading[key] = scope.launch { load(key) }
        }
    }

    fun cancelAll() {
        loading.values.forEach { it.cancel() }
        loading.clear()
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
            failed += key
        } catch (_: IllegalStateException) {
            failed += key
        } finally {
            if (loading[key] === self) loading.remove(key)
        }
    }
}
