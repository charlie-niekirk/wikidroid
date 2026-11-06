package dev.cniekirk.wikidroid.feature.library

import androidx.lifecycle.ViewModel
import dev.cniekirk.wikidroid.core.data.LibraryRepository
import dev.cniekirk.wikidroid.core.data.SettingsRepository
import dev.cniekirk.wikidroid.core.domain.library.ClearHistoryUseCase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * Drives the Library tab: the bookmark and history lists (Room flows, so they update as the reader reads),
 * swipe-to-remove with undo, and clearing the history.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class LibraryViewModel(
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val clearHistory: ClearHistoryUseCase,
) : ViewModel(),
    OrbitContainerHost<LibraryState, LibraryState, LibraryEffect> {
    override val container =
        orbitContainer<LibraryState, LibraryEffect>(LibraryState()) {
            coroutineScope {
                launch {
                    library.bookmarks.collect { entries ->
                        reduce { state.copy(bookmarks = entries.toImmutableList()) }
                    }
                }
                launch {
                    library.history.collect { entries -> reduce { state.copy(history = entries.toImmutableList()) } }
                }
                launch {
                    settings.preferences
                        .map { it.saveHistory }
                        .distinctUntilChanged()
                        .collect { enabled -> reduce { state.copy(saveHistory = enabled) } }
                }
            }
        }

    fun onAction(action: LibraryAction) {
        when (action) {
            // Navigation is the route's job.
            LibraryAction.Back -> {
                Unit
            }

            is LibraryAction.SelectTab -> {
                intent { reduce { state.copy(selectedTab = action.tab) } }
            }

            is LibraryAction.OpenEntry -> {
                intent { postSideEffect(LibraryEffect.OpenArticle(action.title)) }
            }

            is LibraryAction.Remove -> {
                remove(action)
            }

            LibraryAction.Undo -> {
                undo()
            }

            LibraryAction.UndoExpired -> {
                intent { reduce { state.copy(removed = null) } }
            }

            LibraryAction.ClearHistoryRequested -> {
                intent {
                    reduce {
                        state.copy(
                            isClearHistoryConfirmationVisible = true,
                        )
                    }
                }
            }

            LibraryAction.ClearHistoryDismissed -> {
                intent {
                    reduce {
                        state.copy(
                            isClearHistoryConfirmationVisible = false,
                        )
                    }
                }
            }

            LibraryAction.ClearHistoryConfirmed -> {
                confirmClearHistory()
            }
        }
    }

    private fun remove(action: LibraryAction.Remove) =
        intent {
            when (action.tab) {
                LibraryTab.Bookmarks -> library.removeBookmark(action.entry.title)
                LibraryTab.History -> library.removeFromHistory(action.entry.title)
            }
            reduce { state.copy(removed = RemovedEntry(action.tab, action.entry)) }
        }

    private fun undo() =
        intent {
            val removed = state.removed ?: return@intent
            reduce { state.copy(removed = null) }
            when (removed.tab) {
                LibraryTab.Bookmarks -> library.restoreBookmark(removed.entry)
                LibraryTab.History -> library.restoreHistoryEntry(removed.entry)
            }
        }

    private fun confirmClearHistory() =
        intent {
            reduce { state.historyCleared() }
            clearHistory()
        }
}
