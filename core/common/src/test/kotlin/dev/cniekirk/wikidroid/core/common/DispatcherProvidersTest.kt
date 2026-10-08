package dev.cniekirk.wikidroid.core.common

import com.google.common.truth.Truth.assertThat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Test

class DispatcherProvidersTest {
    @DependencyGraph(AppScope::class)
    interface TestGraph {
        @IoDispatcher
        val io: CoroutineDispatcher

        @DefaultDispatcher
        val default: CoroutineDispatcher
    }

    @Test
    fun contributedProviders_resolveInAnAppScopeGraph() {
        val graph = createGraph<TestGraph>()

        assertThat(graph.io).isSameInstanceAs(Dispatchers.IO)
        assertThat(graph.default).isSameInstanceAs(Dispatchers.Default)
    }
}
