package dev.cniekirk.wikidroid.feature.library

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.model.LibraryEntry
import kotlinx.collections.immutable.ImmutableList

enum class LibraryTab { Bookmarks, History }

/** An entry the reader just swiped away, kept so the snackbar's Undo can put it back where it was. */
@Immutable
data class RemovedEntry(
    val tab: LibraryTab,
    val entry: LibraryEntry,
)

/**
 * The Library tab. [bookmarks] and [history] are `null` until Room has delivered them. [saveHistory] only
 * changes what an empty history says. [removed] is the entry the Undo snackbar is currently offering.
 */
@Immutable
data class LibraryState(
    val selectedTab: LibraryTab = LibraryTab.Bookmarks,
    val bookmarks: ImmutableList<LibraryEntry>? = null,
    val history: ImmutableList<LibraryEntry>? = null,
    val saveHistory: Boolean = true,
    val removed: RemovedEntry? = null,
    val isClearHistoryConfirmationVisible: Boolean = false,
)

sealed interface LibraryAction {
    data class SelectTab(
        val tab: LibraryTab,
    ) : LibraryAction

    data class OpenEntry(
        val title: String,
    ) : LibraryAction

    /** Removes [entry] from [tab]'s list and offers to undo it. */
    data class Remove(
        val tab: LibraryTab,
        val entry: LibraryEntry,
    ) : LibraryAction

    /** The snackbar's Undo button. */
    data object Undo : LibraryAction

    /** The snackbar went away without Undo being pressed. */
    data object UndoExpired : LibraryAction

    data object ClearHistoryRequested : LibraryAction

    data object ClearHistoryConfirmed : LibraryAction

    data object ClearHistoryDismissed : LibraryAction
}

sealed interface LibraryEffect {
    data class OpenArticle(
        val title: String,
    ) : LibraryEffect
}
