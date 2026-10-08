package dev.cniekirk.wikidroid

import dev.cniekirk.wikidroid.core.common.IoDispatcher
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher

/** Root dependency graph. Later sessions add the Application/base-URL factory and feature bindings. */
@DependencyGraph(AppScope::class)
interface AppGraph {
    val greeter: Greeter

    /** Resolving this at compile time proves `:core:common`'s contributed providers reach the app graph. */
    @IoDispatcher
    val ioDispatcher: CoroutineDispatcher
}

@Inject
@SingleIn(AppScope::class)
class Greeter {
    val greeting: String = "Hello WikiDroid"
}
