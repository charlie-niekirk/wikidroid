package dev.cniekirk.wikidroid

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Root dependency graph. Later sessions add the Application/base-URL factory and feature bindings. */
@DependencyGraph(AppScope::class)
interface AppGraph {
    val greeter: Greeter
}

@Inject
@SingleIn(AppScope::class)
class Greeter {
    val greeting: String = "Hello WikiDroid"
}
