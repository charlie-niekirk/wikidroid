package dev.cniekirk.wikidroid.core.testing

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRuleTest {
    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @Test
    fun main_runsOnTheInstalledTestDispatcher() {
        var ran = false

        TestScope(testDispatcher).launch(Dispatchers.Main) { ran = true }

        assertThat(ran).isFalse()
        testDispatcher.scheduler.runCurrent()
        assertThat(ran).isTrue()
    }
}
