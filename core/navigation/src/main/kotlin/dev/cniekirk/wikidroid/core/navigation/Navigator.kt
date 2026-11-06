package dev.cniekirk.wikidroid.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Owns one back stack per top-level tab and which tab is selected.
 *
 * Only the selected tab's stack is shown ([backStack]); the others are kept so switching back
 * returns to where the user left off. State is observable from Compose.
 */
@Stable
class Navigator(
    val startTab: TopLevelKey = ExploreKey,
    restoredFrom: NavigatorSnapshot? = null,
) {
    private val stacks: Map<TopLevelKey, MutableList<WikiKey>> =
        TopLevelKeys.associateWith { tab -> mutableStateListOf<WikiKey>(tab) }

    var currentTab: TopLevelKey by mutableStateOf(startTab)
        private set

    /** The selected tab's stack, root first. Never empty: its first element is [currentTab]. */
    val backStack: List<WikiKey> get() = stacks.getValue(currentTab)

    val currentKey: WikiKey get() = backStack.last()

    /** Whether [goBack] has anywhere to go: history on the current tab, or a tab other than [startTab]. */
    val canGoBack: Boolean get() = backStack.size > 1 || currentTab != startTab

    /** [tab]'s stack, root first, whether or not it is selected. */
    fun stack(tab: TopLevelKey): List<WikiKey> = stacks.getValue(tab)

    init {
        restoredFrom?.let { snapshot ->
            snapshot.stacks.forEach { (tab, keys) ->
                // Re-root the stack on its tab so a corrupt snapshot can't leave a tab without a root.
                val stack = stacks.getValue(tab)
                stack.clear()
                stack.add(tab)
                stack.addAll(keys.filterNot { it == tab })
            }
            currentTab = snapshot.currentTab
        }
    }

    /**
     * Top-level keys switch tab (without resetting it); anything else is pushed on the current tab.
     * Pushing the key that is already on top is ignored so a double tap doesn't stack two copies.
     */
    fun navigate(key: WikiKey) {
        when (key) {
            is TopLevelKey -> {
                switchTab(key)
            }

            else -> {
                val stack = stacks.getValue(currentTab)
                if (stack.last() != key) stack.add(key)
            }
        }
    }

    fun switchTab(tab: TopLevelKey) {
        currentTab = tab
    }

    /** Re-selecting the current tab pops it back to its root. */
    fun reselectTab(tab: TopLevelKey) {
        if (tab == currentTab) popToRoot() else switchTab(tab)
    }

    fun popToRoot() {
        val stack = stacks.getValue(currentTab)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /**
     * Pops the current tab, or from the root of any other tab returns to [startTab].
     * Returns `false` when there is nowhere left to go, so the caller can let the system handle back.
     */
    fun goBack(): Boolean {
        if (!canGoBack) return false
        val stack = stacks.getValue(currentTab)
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else currentTab = startTab
        return true
    }

    fun snapshot(): NavigatorSnapshot = NavigatorSnapshot(currentTab, stacks.mapValues { (_, stack) -> stack.toList() })

    companion object {
        // Map keys are sealed objects, which JSON only supports in the structured (array) form.
        private val json = Json { allowStructuredMapKeys = true }

        /**
         * Saves to a JSON string so it survives process death through `rememberSaveable`. A snapshot that no longer
         * decodes (one saved when a removed key was a tab) restores nothing, so the app starts afresh.
         */
        fun saver(startTab: TopLevelKey = ExploreKey): Saver<Navigator, String> =
            Saver(
                save = { json.encodeToString(NavigatorSnapshot.serializer(), it.snapshot()) },
                restore = { saved ->
                    try {
                        Navigator(startTab, json.decodeFromString(NavigatorSnapshot.serializer(), saved))
                    } catch (_: IllegalArgumentException) {
                        // SerializationException is an IllegalArgumentException.
                        null
                    }
                },
            )
    }
}

@Serializable
data class NavigatorSnapshot(
    val currentTab: TopLevelKey,
    val stacks: Map<TopLevelKey, List<WikiKey>>,
)

@Composable
fun rememberNavigator(startTab: TopLevelKey = ExploreKey): Navigator =
    rememberSaveable(saver = Navigator.saver(startTab)) { Navigator(startTab) }
