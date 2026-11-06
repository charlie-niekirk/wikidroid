package dev.cniekirk.wikidroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.cniekirk.wikidroid.core.navigation.ArticleKey
import dev.cniekirk.wikidroid.core.navigation.EntryProviderInstaller
import dev.cniekirk.wikidroid.core.navigation.Navigator
import dev.cniekirk.wikidroid.core.navigation.TopLevelKey
import dev.cniekirk.wikidroid.core.navigation.TopLevelKeys
import dev.cniekirk.wikidroid.core.navigation.WikiKey
import dev.cniekirk.wikidroid.core.ui.pane.WikiPanes
import kotlinx.collections.immutable.ImmutableSet

/**
 * The app's chrome: a bottom bar (or rail, on wide windows) with one tab per [TopLevelKeys] entry, and a
 * [NavDisplay] showing the selected tab's back stack.
 *
 * Screens come from [installers], one per feature module. A destination nobody has registered yet
 * shows a [PlaceholderScreen].
 */
@Composable
fun AppShell(
    navigator: Navigator,
    installers: ImmutableSet<EntryProviderInstaller>,
    modifier: Modifier = Modifier,
) {
    NavigationSuiteScaffold(
        navigationItems = {
            TopLevelKeys.forEach { tab ->
                val label = stringResource(tab.labelRes)
                NavigationSuiteItem(
                    selected = tab == navigator.currentTab,
                    onClick = { navigator.reselectTab(tab) },
                    icon = { Icon(painter = painterResource(tab.iconRes), contentDescription = null) },
                    label = { Text(label) },
                )
            }
        },
        modifier = modifier,
    ) {
        AppNavDisplay(navigator = navigator, installers = installers)
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AppNavDisplay(
    navigator: Navigator,
    installers: ImmutableSet<EntryProviderInstaller>,
) {
    val entryProvider =
        remember(navigator, installers) {
            entryProvider<NavKey>(
                fallback = { key -> placeholderEntry(key) },
            ) {
                installers.forEach { installer -> with(installer) { install(navigator) } }
            }
        }

    // Every tab's entries are decorated on every composition, each tab with its own decorators. Handing one set
    // of decorators only the selected tab's stack would treat the other tabs' entries as popped and clear their
    // view models and saved state. Never skip a tab here, or its state is dropped.
    val entriesByTab =
        TopLevelKeys.associateWith { tab ->
            key(tab) { rememberTabEntries(navigator, tab, entryProvider) }
        }

    // NavDisplay handles back only while a tab has history. From the root of any tab but the first, go to the first.
    BackHandler(enabled = navigator.backStack.size == 1 && navigator.canGoBack) {
        navigator.goBack()
    }

    // An entry is identified by its key alone, so without this the same article on top of two tabs (or two
    // list-detail scenes) would be taken for one scene when switching tabs, and share its saved state.
    key(navigator.currentTab) {
        NavDisplay(
            entries = entriesByTab.getValue(navigator.currentTab),
            // The covered screen slides part way, so clip it to the display or it shows under the rail.
            modifier = Modifier.fillMaxSize().clipToBounds(),
            sceneStrategies = listOf(rememberListDetailSceneStrategy()),
            transitionSpec = NavTransitions.push,
            popTransitionSpec = NavTransitions.pop,
            predictivePopTransitionSpec = NavTransitions.predictivePop,
            onBack = { navigator.goBack() },
        )
    }
}

/** [tab]'s entries, with saved state and view models scoped to that tab and kept until an entry is popped. */
@Composable
private fun rememberTabEntries(
    navigator: Navigator,
    tab: TopLevelKey,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): List<NavEntry<NavKey>> =
    rememberDecoratedNavEntries<NavKey>(
        backStack = navigator.stack(tab),
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider = entryProvider,
    )

/**
 * The fallback for a key no feature has registered. `:feature:article` registers the real article screen, so this
 * stub only shows for an article when that module is absent. It is a detail pane like the real one.
 */
private fun placeholderEntry(key: NavKey): NavEntry<NavKey> =
    NavEntry(key, metadata = if (key is ArticleKey) WikiPanes.detail() else emptyMap()) {
        (key as? WikiKey)?.let { PlaceholderScreen(key = it) }
    }
