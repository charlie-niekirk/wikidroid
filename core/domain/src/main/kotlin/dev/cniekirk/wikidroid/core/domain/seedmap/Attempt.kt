package dev.cniekirk.wikidroid.core.domain.seedmap

import kotlinx.coroutines.CancellationException

/**
 * The result of [block], or null if the engine refused or failed the request, which leaves the map without
 * that answer rather than taking the app down. Cancellation still propagates.
 */
internal inline fun <T> attempt(block: () -> T): T? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }
