package dev.cniekirk.wikidroid.core.navigation

import androidx.compose.runtime.saveable.SaverScope
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavigatorTest {
    private val diamond = ArticleKey("Diamond")
    private val creeper = ArticleKey("Creeper", anchor = "Behavior")
    private val mobs = CategoryKey("Hostile mobs")

    @Test
    fun startsOnTheStartTabWithOnlyItsRoot() {
        val navigator = Navigator()

        assertThat(navigator.currentTab).isEqualTo(ExploreKey)
        assertThat(navigator.backStack).containsExactly(ExploreKey)
        assertThat(navigator.currentKey).isEqualTo(ExploreKey)
    }

    @Test
    fun navigate_pushesOntoTheCurrentTab() {
        val navigator = Navigator()

        navigator.navigate(mobs)
        navigator.navigate(creeper)

        assertThat(navigator.backStack).containsExactly(ExploreKey, mobs, creeper).inOrder()
        assertThat(navigator.currentKey).isEqualTo(creeper)
    }

    @Test
    fun navigate_ignoresTheKeyAlreadyOnTop() {
        val navigator = Navigator()

        navigator.navigate(diamond)
        navigator.navigate(diamond)

        assertThat(navigator.backStack).containsExactly(ExploreKey, diamond).inOrder()
    }

    @Test
    fun navigate_withATopLevelKeySwitchesTab() {
        val navigator = Navigator()

        navigator.navigate(ToolsKey)

        assertThat(navigator.currentTab).isEqualTo(ToolsKey)
        assertThat(navigator.backStack).containsExactly(ToolsKey)
    }

    @Test
    fun goBack_popsTheTopKey() {
        val navigator = Navigator()
        navigator.navigate(diamond)

        val handled = navigator.goBack()

        assertThat(handled).isTrue()
        assertThat(navigator.backStack).containsExactly(ExploreKey)
    }

    @Test
    fun goBack_atTheRootOfAnotherTabReturnsToTheStartTab() {
        val navigator = Navigator()
        navigator.switchTab(SettingsKey)

        val handled = navigator.goBack()

        assertThat(handled).isTrue()
        assertThat(navigator.currentTab).isEqualTo(ExploreKey)
    }

    @Test
    fun goBack_atTheRootOfTheStartTabIsNotHandled() {
        val navigator = Navigator()

        assertThat(navigator.goBack()).isFalse()
        assertThat(navigator.backStack).containsExactly(ExploreKey)
    }

    @Test
    fun switchTab_keepsEachTabsBackStack() {
        val navigator = Navigator()
        navigator.navigate(diamond)
        navigator.switchTab(SearchKey)
        navigator.navigate(creeper)

        navigator.switchTab(ExploreKey)
        assertThat(navigator.backStack).containsExactly(ExploreKey, diamond).inOrder()

        navigator.switchTab(SearchKey)
        assertThat(navigator.backStack).containsExactly(SearchKey, creeper).inOrder()
    }

    @Test
    fun reselectTab_popsTheCurrentTabToItsRoot() {
        val navigator = Navigator()
        navigator.navigate(mobs)
        navigator.navigate(diamond)

        navigator.reselectTab(ExploreKey)

        assertThat(navigator.backStack).containsExactly(ExploreKey)
    }

    @Test
    fun reselectTab_onAnotherTabJustSwitchesToIt() {
        val navigator = Navigator()
        navigator.navigate(diamond)

        navigator.reselectTab(ToolsKey)

        assertThat(navigator.currentTab).isEqualTo(ToolsKey)
        navigator.switchTab(ExploreKey)
        assertThat(navigator.backStack).containsExactly(ExploreKey, diamond).inOrder()
    }

    @Test
    fun snapshot_restoresEveryTabAndTheSelection() {
        val original = Navigator()
        original.navigate(mobs)
        original.navigate(creeper)
        original.switchTab(ToolsKey)
        original.navigate(diamond)

        val restored = Navigator(restoredFrom = original.snapshot())

        assertThat(restored.currentTab).isEqualTo(ToolsKey)
        assertThat(restored.backStack).containsExactly(ToolsKey, diamond).inOrder()
        restored.switchTab(ExploreKey)
        assertThat(restored.backStack).containsExactly(ExploreKey, mobs, creeper).inOrder()
    }

    @Test
    fun restore_rebuildsTheRootWhenASnapshotLacksIt() {
        val snapshot = NavigatorSnapshot(currentTab = SearchKey, stacks = mapOf(SearchKey to listOf(diamond)))

        val navigator = Navigator(restoredFrom = snapshot)

        assertThat(navigator.backStack).containsExactly(SearchKey, diamond).inOrder()
    }

    @Test
    fun theSeedMapIsTheMiddleTab() {
        assertThat(TopLevelKeys).containsExactly(ExploreKey, SearchKey, SeedMapKey, ToolsKey, SettingsKey).inOrder()
    }

    @Test
    fun anArticleOpenedFromTheSeedMapStaysOnItsTab() {
        val navigator = Navigator()
        navigator.navigate(SeedMapKey)

        navigator.navigate(ArticleKey("Village"))

        assertThat(navigator.currentTab).isEqualTo(SeedMapKey)
        assertThat(navigator.backStack).containsExactly(SeedMapKey, ArticleKey("Village")).inOrder()
        navigator.switchTab(ExploreKey)
        assertThat(navigator.backStack).containsExactly(ExploreKey)
    }

    @Test
    fun stack_showsAnyTabWithoutSelectingIt() {
        val navigator = Navigator()
        navigator.navigate(diamond)
        navigator.switchTab(SearchKey)
        navigator.navigate(mobs)

        assertThat(navigator.stack(ExploreKey)).containsExactly(ExploreKey, diamond).inOrder()
        assertThat(navigator.stack(SearchKey)).containsExactly(SearchKey, mobs).inOrder()
        assertThat(navigator.stack(ToolsKey)).containsExactly(ToolsKey)
        assertThat(navigator.currentTab).isEqualTo(SearchKey)
    }

    @Test
    fun canGoBack_followsWhatGoBackWouldDo() {
        val navigator = Navigator()
        assertThat(navigator.canGoBack).isFalse()

        navigator.navigate(diamond)
        assertThat(navigator.canGoBack).isTrue()
        navigator.goBack()
        assertThat(navigator.canGoBack).isFalse()

        navigator.switchTab(ToolsKey)
        assertThat(navigator.canGoBack).isTrue()
        assertThat(navigator.goBack()).isTrue()
        assertThat(navigator.currentTab).isEqualTo(ExploreKey)
        assertThat(navigator.goBack()).isFalse()
    }

    @Test
    fun navigate_pushesTheLibraryOnTheCurrentTab() {
        val navigator = Navigator()
        navigator.switchTab(SearchKey)

        navigator.navigate(LibraryKey)

        assertThat(navigator.currentTab).isEqualTo(SearchKey)
        assertThat(navigator.backStack).containsExactly(SearchKey, LibraryKey).inOrder()
    }

    @Test
    fun saver_restoresNothingFromASnapshotWithATabThatIsGone() {
        val saver = Navigator.saver()
        val navigator = Navigator()
        navigator.switchTab(ToolsKey)
        val saved = with(saver) { SaverScope { true }.save(navigator) }
        // The Library was a tab until the Tools tab replaced it.
        val fromAnOlderVersion = checkNotNull(saved).replace("ToolsKey", "LibraryKey")

        assertThat(saver.restore(fromAnOlderVersion)).isNull()
        assertThat(saver.restore(saved)?.currentTab).isEqualTo(ToolsKey)
    }
}
