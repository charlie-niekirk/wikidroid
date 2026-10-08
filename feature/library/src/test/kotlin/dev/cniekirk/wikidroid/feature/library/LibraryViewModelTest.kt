package dev.cniekirk.wikidroid.feature.library

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.testing.MainDispatcherRule
import dev.cniekirk.wikidroid.core.testing.fake.FakeLibraryRepository
import dev.cniekirk.wikidroid.core.testing.fake.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.orbitmvi.orbit.test.OrbitTestContext
import org.orbitmvi.orbit.test.test

class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val settings = FakeSettingsRepository()
    private val viewModel = LibraryViewModel(library, settings)

    private val state get() = viewModel.container.stateFlow.value

    private fun page(title: String) = ArticleSummary(title = title)

    private suspend fun TestScope.inLibrary(
        body: suspend OrbitTestContext<LibraryState, LibraryEffect, LibraryViewModel>.() -> Unit,
    ) {
        viewModel.test(this) {
            runOnCreate()
            runCurrent()
            body()
            cancelAndIgnoreRemainingItems()
        }
    }

    private fun TestScope.act(action: LibraryAction) {
        viewModel.onAction(action)
        runCurrent()
    }

    private fun bookmarkEntry(title: String) = state.bookmarks!!.first { it.title == title }

    // region lists

    @Test
    fun theListsAreNotKnownUntilTheRepositoryHasDelivered() =
        runTest {
            assertThat(state.bookmarks).isNull()
            assertThat(state.history).isNull()
            assertThat(state.selectedTab).isEqualTo(LibraryTab.Bookmarks)
        }

    @Test
    fun showsBookmarksAndHistoryNewestFirst() =
        runTest {
            library.addBookmark(page("Diamond"))
            library.addBookmark(page("Creeper"))
            library.recordVisit(page("Zombie"))
            library.recordVisit(page("Alex"))

            inLibrary {
                assertThat(state.bookmarks!!.map { it.title }).containsExactly("Creeper", "Diamond").inOrder()
                assertThat(state.history!!.map { it.title }).containsExactly("Alex", "Zombie").inOrder()
            }
        }

    @Test
    fun anEmptyLibraryIsEmptyRatherThanUnknown() =
        runTest {
            inLibrary {
                assertThat(state.bookmarks).isEmpty()
                assertThat(state.history).isEmpty()
            }
        }

    @Test
    fun theListsFollowChangesMadeElsewhere() =
        runTest {
            inLibrary {
                library.addBookmark(page("Diamond"))
                runCurrent()
                assertThat(state.bookmarks!!.map { it.title }).containsExactly("Diamond")

                library.removeBookmark("Diamond")
                runCurrent()
                assertThat(state.bookmarks).isEmpty()
            }
        }

    @Test
    fun entriesReportWhetherTheyAreAvailableOffline() =
        runTest {
            library.addBookmark(page("Diamond"))
            library.addBookmark(page("Creeper"))
            library.offlineTitles += "Diamond"

            inLibrary {
                assertThat(bookmarkEntry("Diamond").isAvailableOffline).isTrue()
                assertThat(bookmarkEntry("Creeper").isAvailableOffline).isFalse()
            }
        }

    @Test
    fun followsTheSaveHistoryPreference() =
        runTest {
            inLibrary {
                assertThat(state.saveHistory).isTrue()

                settings.setSaveHistory(false)
                runCurrent()

                assertThat(state.saveHistory).isFalse()
            }
        }

    // endregion

    // region navigation

    @Test
    fun selectingATabShowsIt() =
        runTest {
            inLibrary {
                act(LibraryAction.SelectTab(LibraryTab.History))
                assertThat(state.selectedTab).isEqualTo(LibraryTab.History)

                act(LibraryAction.SelectTab(LibraryTab.Bookmarks))
                assertThat(state.selectedTab).isEqualTo(LibraryTab.Bookmarks)
            }
        }

    @Test
    fun openingAnEntryAsksToOpenItsArticle() =
        runTest {
            inLibrary {
                act(LibraryAction.OpenEntry("Diamond"))

                // The two lists arriving are state items ahead of the effect in the stream.
                skipItems(2)
                expectSideEffect(LibraryEffect.OpenArticle("Diamond"))
            }
        }

    // endregion

    // region removing and undoing

    @Test
    fun removingABookmarkDeletesItAndOffersUndo() =
        runTest {
            library.addBookmark(page("Diamond"))
            library.addBookmark(page("Creeper"))

            inLibrary {
                val diamond = bookmarkEntry("Diamond")

                act(LibraryAction.Remove(LibraryTab.Bookmarks, diamond))

                assertThat(state.bookmarks!!.map { it.title }).containsExactly("Creeper")
                assertThat(state.removed).isEqualTo(RemovedEntry(LibraryTab.Bookmarks, diamond))
            }
        }

    @Test
    fun undoPutsTheBookmarkBackWithItsOriginalTimestamp() =
        runTest {
            library.addBookmark(page("Diamond"))
            library.addBookmark(page("Creeper"))

            inLibrary {
                val diamond = bookmarkEntry("Diamond")
                act(LibraryAction.Remove(LibraryTab.Bookmarks, diamond))

                act(LibraryAction.Undo)

                assertThat(state.removed).isNull()
                // Diamond was saved first, so it goes back under Creeper rather than to the top.
                assertThat(state.bookmarks!!.map { it.title }).containsExactly("Creeper", "Diamond").inOrder()
                assertThat(bookmarkEntry("Diamond").timestamp).isEqualTo(diamond.timestamp)
            }
        }

    @Test
    fun removingAHistoryEntryLeavesBookmarksAlone() =
        runTest {
            library.addBookmark(page("Diamond"))
            library.recordVisit(page("Diamond"))
            library.recordVisit(page("Creeper"))

            inLibrary {
                val creeper = state.history!!.first { it.title == "Creeper" }

                act(LibraryAction.Remove(LibraryTab.History, creeper))

                assertThat(state.history!!.map { it.title }).containsExactly("Diamond")
                assertThat(state.bookmarks!!.map { it.title }).containsExactly("Diamond")
                assertThat(state.removed).isEqualTo(RemovedEntry(LibraryTab.History, creeper))
            }
        }

    @Test
    fun undoPutsAHistoryEntryBack() =
        runTest {
            library.recordVisit(page("Diamond"))
            library.recordVisit(page("Creeper"))

            inLibrary {
                val diamond = state.history!!.first { it.title == "Diamond" }
                act(LibraryAction.Remove(LibraryTab.History, diamond))

                act(LibraryAction.Undo)

                assertThat(state.history!!.map { it.title }).containsExactly("Creeper", "Diamond").inOrder()
            }
        }

    @Test
    fun undoRestoresEvenWhenTheHistoryPreferenceIsOffNow() =
        runTest {
            library.recordVisit(page("Diamond"))

            inLibrary {
                act(LibraryAction.Remove(LibraryTab.History, state.history!!.single()))
                library.saveHistory = false

                act(LibraryAction.Undo)

                assertThat(state.history!!.map { it.title }).containsExactly("Diamond")
            }
        }

    @Test
    fun whenTheSnackbarExpiresTheEntryStaysRemoved() =
        runTest {
            library.addBookmark(page("Diamond"))

            inLibrary {
                act(LibraryAction.Remove(LibraryTab.Bookmarks, bookmarkEntry("Diamond")))

                act(LibraryAction.UndoExpired)
                act(LibraryAction.Undo)

                assertThat(state.removed).isNull()
                assertThat(state.bookmarks).isEmpty()
            }
        }

    @Test
    fun undoWithNothingRemovedDoesNothing() =
        runTest {
            library.addBookmark(page("Diamond"))

            inLibrary {
                act(LibraryAction.Undo)

                assertThat(state.bookmarks!!.map { it.title }).containsExactly("Diamond")
            }
        }

    @Test
    fun aSecondRemovalReplacesTheFirstUndo() =
        runTest {
            library.addBookmark(page("Diamond"))
            library.addBookmark(page("Creeper"))

            inLibrary {
                val diamond = bookmarkEntry("Diamond")
                val creeper = bookmarkEntry("Creeper")
                act(LibraryAction.Remove(LibraryTab.Bookmarks, diamond))
                act(LibraryAction.Remove(LibraryTab.Bookmarks, creeper))

                act(LibraryAction.Undo)

                assertThat(state.bookmarks!!.map { it.title }).containsExactly("Creeper")
            }
        }

    // endregion

    // region clearing history

    @Test
    fun clearingHistoryAsksFirst() =
        runTest {
            library.recordVisit(page("Diamond"))

            inLibrary {
                act(LibraryAction.ClearHistoryRequested)

                assertThat(state.isClearHistoryConfirmationVisible).isTrue()
                assertThat(state.history).hasSize(1)
            }
        }

    @Test
    fun decliningTheDialogKeepsTheHistory() =
        runTest {
            library.recordVisit(page("Diamond"))

            inLibrary {
                act(LibraryAction.ClearHistoryRequested)
                act(LibraryAction.ClearHistoryDismissed)

                assertThat(state.isClearHistoryConfirmationVisible).isFalse()
                assertThat(state.history).hasSize(1)
            }
        }

    @Test
    fun confirmingClearsTheHistoryButNotTheBookmarks() =
        runTest {
            library.addBookmark(page("Diamond"))
            library.recordVisit(page("Diamond"))
            library.recordVisit(page("Creeper"))

            inLibrary {
                act(LibraryAction.ClearHistoryRequested)
                act(LibraryAction.ClearHistoryConfirmed)

                assertThat(state.isClearHistoryConfirmationVisible).isFalse()
                assertThat(state.history).isEmpty()
                assertThat(state.bookmarks).hasSize(1)
            }
        }

    @Test
    fun clearingHistoryForgetsAHistoryUndoButKeepsABookmarkUndo() =
        runTest {
            library.addBookmark(page("Diamond"))
            library.recordVisit(page("Creeper"))

            inLibrary {
                act(LibraryAction.Remove(LibraryTab.History, state.history!!.single()))
                act(LibraryAction.ClearHistoryConfirmed)
                assertThat(state.removed).isNull()

                val diamond = bookmarkEntry("Diamond")
                act(LibraryAction.Remove(LibraryTab.Bookmarks, diamond))
                act(LibraryAction.ClearHistoryConfirmed)
                assertThat(state.removed).isEqualTo(RemovedEntry(LibraryTab.Bookmarks, diamond))
            }
        }

    // endregion
}
