package dev.cniekirk.wikidroid.feature.library

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.LibraryEntry
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import kotlin.time.Instant

class LibraryScreenTest : ComposeTest() {
    private val actions = mutableListOf<LibraryAction>()

    private val diamond = entry("Diamond")
    private val creeper = entry("Creeper", offline = true)

    private fun entry(
        title: String,
        offline: Boolean = false,
    ) = LibraryEntry(title, title, null, Instant.fromEpochSeconds(1_700_000_000), offline)

    private fun setScreen(state: LibraryState) {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                LibraryScreen(state = state, onAction = { actions += it })
            }
        }
    }

    private fun loaded(
        tab: LibraryTab = LibraryTab.Bookmarks,
        bookmarks: List<LibraryEntry> = listOf(diamond, creeper),
        history: List<LibraryEntry> = listOf(creeper),
        saveHistory: Boolean = true,
    ) = LibraryState(
        selectedTab = tab,
        bookmarks = persistentListOf(*bookmarks.toTypedArray()),
        history = persistentListOf(*history.toTypedArray()),
        saveHistory = saveHistory,
    )

    // region content

    @Test
    fun showsASpinnerUntilTheListsArrive() {
        setScreen(LibraryState())

        composeRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun listsTheBookmarks() {
        setScreen(loaded())

        composeRule.onNodeWithText("Diamond").assertIsDisplayed()
        composeRule.onNodeWithText("Creeper").assertIsDisplayed()
    }

    @Test
    fun listsTheHistoryOnItsTab() {
        setScreen(loaded(tab = LibraryTab.History, bookmarks = listOf(diamond), history = listOf(creeper)))

        composeRule.onNodeWithText("Creeper").assertIsDisplayed()
        composeRule.onNodeWithText("Diamond").assertDoesNotExist()
    }

    @Test
    fun theSelectedTabIsMarked() {
        setScreen(loaded(tab = LibraryTab.History))

        composeRule.onNodeWithText("History").assertIsSelected()
    }

    @Test
    fun anEntryShowsWhenItWasSavedOrViewed() {
        setScreen(loaded(history = listOf(creeper)))

        // The exact wording depends on today's date; the prefix says which list it is.
        composeRule.onAllNodes(hasText("Saved", substring = true)).assertCountEquals(2)
    }

    @Test
    fun onlyOfflineEntriesCarryTheBadge() {
        setScreen(loaded())

        composeRule.onAllNodesWithContentDescription("Available offline").assertCountEquals(1)
    }

    // endregion

    // region empty states

    @Test
    fun explainsHowToBookmarkWhenThereAreNone() {
        setScreen(loaded(bookmarks = emptyList()))

        composeRule.onNodeWithText("No bookmarks yet").assertIsDisplayed()
    }

    @Test
    fun saysWhenTheHistoryIsEmpty() {
        setScreen(loaded(tab = LibraryTab.History, history = emptyList()))

        composeRule.onNodeWithText("No history yet").assertIsDisplayed()
    }

    @Test
    fun saysWhenTheHistoryIsEmptyBecauseItIsTurnedOff() {
        setScreen(loaded(tab = LibraryTab.History, history = emptyList(), saveHistory = false))

        composeRule.onNodeWithText("History is turned off").assertIsDisplayed()
    }

    // endregion

    // region interactions

    @Test
    fun tappingATabSelectsIt() {
        setScreen(loaded())

        composeRule.onNodeWithText("History").performClick()

        assertThat(actions).containsExactly(LibraryAction.SelectTab(LibraryTab.History))
    }

    @Test
    fun tappingAnEntryOpensIt() {
        setScreen(loaded())

        composeRule.onNodeWithText("Diamond").performClick()

        assertThat(actions).containsExactly(LibraryAction.OpenEntry("Diamond"))
    }

    @Test
    fun swipingAnEntryAwayRemovesIt() {
        setScreen(loaded())

        composeRule.onNodeWithText("Diamond").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertThat(actions).containsExactly(LibraryAction.Remove(LibraryTab.Bookmarks, diamond))
    }

    @Test
    fun swipingTheOtherWayRemovesItToo() {
        setScreen(loaded(tab = LibraryTab.History))

        composeRule.onNodeWithText("Creeper").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertThat(actions).containsExactly(LibraryAction.Remove(LibraryTab.History, creeper))
    }

    @Test
    fun removalIsAlsoAnAccessibilityAction() {
        setScreen(loaded())

        val node = composeRule.onNodeWithText("Diamond").fetchSemanticsNode()
        val custom = node.config[SemanticsActions.CustomActions].single { it.label == "Remove Diamond" }
        composeRule.runOnUiThread { custom.action() }
        composeRule.waitForIdle()

        assertThat(actions).containsExactly(LibraryAction.Remove(LibraryTab.Bookmarks, diamond))
    }

    // endregion

    // region undo snackbar

    @Test
    fun offersUndoForARemovedBookmark() {
        setScreen(loaded().copy(removed = RemovedEntry(LibraryTab.Bookmarks, diamond)))

        composeRule.onNodeWithText("Removed “Diamond” from bookmarks").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    fun wordsTheHistorySnackbarDifferently() {
        setScreen(loaded(tab = LibraryTab.History).copy(removed = RemovedEntry(LibraryTab.History, creeper)))

        composeRule.onNodeWithText("Removed “Creeper” from history").assertIsDisplayed()
    }

    @Test
    fun pressingUndoReportsIt() {
        setScreen(loaded().copy(removed = RemovedEntry(LibraryTab.Bookmarks, diamond)))

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertThat(actions).containsExactly(LibraryAction.Undo)
    }

    @Test
    fun theSnackbarTimingOutReportsThatUndoIsGone() {
        composeRule.mainClock.autoAdvance = false
        setScreen(loaded().copy(removed = RemovedEntry(LibraryTab.Bookmarks, diamond)))
        composeRule.mainClock.advanceTimeBy(500)
        assertThat(actions).isEmpty()

        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()

        assertThat(actions).containsExactly(LibraryAction.UndoExpired)
    }

    @Test
    fun noSnackbarWhenNothingWasRemoved() {
        setScreen(loaded())

        composeRule.onNodeWithText("Undo").assertDoesNotExist()
    }

    // endregion

    // region clearing history

    @Test
    fun theClearButtonIsOnlyOnTheHistoryTab() {
        setScreen(loaded(tab = LibraryTab.Bookmarks))

        composeRule.onNodeWithContentDescription("Clear history").assertDoesNotExist()
    }

    @Test
    fun theClearButtonIsHiddenWhenThereIsNothingToClear() {
        setScreen(loaded(tab = LibraryTab.History, history = emptyList()))

        composeRule.onNodeWithContentDescription("Clear history").assertDoesNotExist()
    }

    @Test
    fun theClearButtonAsksForConfirmation() {
        setScreen(loaded(tab = LibraryTab.History))

        composeRule.onNodeWithContentDescription("Clear history").performClick()

        assertThat(actions).containsExactly(LibraryAction.ClearHistoryRequested)
    }

    @Test
    fun theDialogConfirmsOrCancels() {
        setScreen(loaded(tab = LibraryTab.History).copy(isClearHistoryConfirmationVisible = true))

        composeRule.onNodeWithText("Clear history?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Clear").performClick()

        assertThat(
            actions,
        ).containsExactly(LibraryAction.ClearHistoryDismissed, LibraryAction.ClearHistoryConfirmed).inOrder()
    }

    @Test
    fun noDialogUnlessAsked() {
        setScreen(loaded(tab = LibraryTab.History))

        composeRule.onNodeWithText("Clear history?").assertDoesNotExist()
    }

    // endregion
}
