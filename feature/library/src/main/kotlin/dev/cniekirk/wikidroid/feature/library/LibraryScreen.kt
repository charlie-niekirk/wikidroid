package dev.cniekirk.wikidroid.feature.library

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIconButton
import dev.cniekirk.wikidroid.core.designsystem.component.WikiTopAppBar
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.LibraryEntry
import dev.cniekirk.wikidroid.core.ui.ArticleCard
import dev.cniekirk.wikidroid.core.ui.EmptyState
import dev.cniekirk.wikidroid.core.ui.LoadingState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant
import dev.cniekirk.wikidroid.core.ui.R as UiR

internal const val LIBRARY_LIST_TAG = "library-list"

/**
 * The Library tab: bookmarks and history. Swiping a row away removes it and offers Undo in a snackbar.
 * Stateless: the route owns the ViewModel and navigation.
 */
@Composable
fun LibraryScreen(
    state: LibraryState,
    onAction: (LibraryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    RemovalSnackbar(removed = state.removed, snackbarHostState = snackbarHostState, onAction = onAction)

    Scaffold(
        modifier = modifier,
        topBar = { LibraryTopBar(state = state, onAction = onAction) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val entries =
            when (state.selectedTab) {
                LibraryTab.Bookmarks -> state.bookmarks
                LibraryTab.History -> state.history
            }
        when {
            entries == null -> {
                LoadingState(modifier = Modifier.padding(padding))
            }

            entries.isEmpty() -> {
                EmptyLibrary(
                    tab = state.selectedTab,
                    saveHistory = state.saveHistory,
                    modifier = Modifier.padding(padding),
                )
            }

            else -> {
                EntryList(
                    tab = state.selectedTab,
                    entries = entries,
                    onAction = onAction,
                    contentPadding = padding,
                )
            }
        }
    }

    if (state.isClearHistoryConfirmationVisible) {
        ClearHistoryDialog(
            onConfirm = { onAction(LibraryAction.ClearHistoryConfirmed) },
            onDismiss = { onAction(LibraryAction.ClearHistoryDismissed) },
        )
    }
}

@Composable
private fun LibraryTopBar(
    state: LibraryState,
    onAction: (LibraryAction) -> Unit,
) {
    Column {
        WikiTopAppBar(
            title = stringResource(R.string.library_title),
            actions = {
                if (state.selectedTab == LibraryTab.History && !state.history.isNullOrEmpty()) {
                    WikiIconButton(
                        icon = WikiIcons.Delete,
                        contentDescription = stringResource(R.string.library_clear_history),
                        onClick = { onAction(LibraryAction.ClearHistoryRequested) },
                    )
                }
            },
        )
        PrimaryTabRow(selectedTabIndex = state.selectedTab.ordinal) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = tab == state.selectedTab,
                    onClick = { onAction(LibraryAction.SelectTab(tab)) },
                    text = { Text(stringResource(tab.label)) },
                )
            }
        }
    }
}

/** Shows the Undo snackbar for [removed] and reports what the reader did with it. */
@Composable
private fun RemovalSnackbar(
    removed: RemovedEntry?,
    snackbarHostState: SnackbarHostState,
    onAction: (LibraryAction) -> Unit,
) {
    val currentOnAction by rememberUpdatedState(onAction)
    val message =
        removed?.let {
            stringResource(
                when (it.tab) {
                    LibraryTab.Bookmarks -> R.string.library_bookmark_removed
                    LibraryTab.History -> R.string.library_history_removed
                },
                it.entry.displayTitle,
            )
        }
    val undoLabel = stringResource(R.string.library_undo)
    // A newer removal (or a cleared history) cancels this effect, which dismisses the old snackbar.
    LaunchedEffect(removed) {
        if (message == null) return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
        currentOnAction(if (result == SnackbarResult.ActionPerformed) LibraryAction.Undo else LibraryAction.UndoExpired)
    }
}

@Composable
private fun EntryList(
    tab: LibraryTab,
    entries: ImmutableList<LibraryEntry>,
    onAction: (LibraryAction) -> Unit,
    contentPadding: PaddingValues,
) {
    // Taken once per tab visit: the rows read "5 minutes ago" relative to when the list was shown.
    val now = remember(tab) { System.currentTimeMillis() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(LIBRARY_LIST_TAG),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.title }) { entry ->
            LibraryRow(
                tab = tab,
                entry = entry,
                now = now,
                onOpen = { onAction(LibraryAction.OpenEntry(entry.title)) },
                onRemove = { onAction(LibraryAction.Remove(tab, entry)) },
            )
        }
    }
}

@Composable
private fun LibraryRow(
    tab: LibraryTab,
    entry: LibraryEntry,
    now: Long,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val removeLabel = stringResource(R.string.library_remove_entry, entry.displayTitle)
    SwipeToDismissBox(
        state = dismissState,
        onDismiss = { onRemove() },
        backgroundContent = {
            val towardsEnd = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            color =
                                if (dismissState.dismissDirection == SwipeToDismissBoxValue.Settled) {
                                    Color.Transparent
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                            shape = RoundedCornerShape(12.dp),
                        ).padding(horizontal = 20.dp),
                contentAlignment = if (towardsEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    WikiIcon(
                        icon = WikiIcons.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
    ) {
        ArticleCard(
            article = entry.toSummary(tab, now),
            onClick = onOpen,
            // Swiping isn't available to everyone, so removal is also an accessibility action.
            modifier =
                Modifier.semantics {
                    customActions =
                        listOf(CustomAccessibilityAction(removeLabel) { onRemove().let { true } })
                },
            trailingContent =
                if (entry.isAvailableOffline) {
                    {
                        WikiIcon(
                            icon = WikiIcons.OfflinePin,
                            contentDescription = stringResource(UiR.string.ui_offline_available),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
        )
    }
}

@Composable
private fun LibraryEntry.toSummary(
    tab: LibraryTab,
    now: Long,
): ArticleSummary {
    val ago =
        DateUtils
            .getRelativeTimeSpanString(
                timestamp.toEpochMilliseconds(),
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
    return ArticleSummary(
        title = title,
        displayTitle = displayTitle,
        description =
            stringResource(
                when (tab) {
                    LibraryTab.Bookmarks -> R.string.library_saved_ago
                    LibraryTab.History -> R.string.library_viewed_ago
                },
                ago,
            ),
        thumbnailUrl = thumbnailUrl,
    )
}

@Composable
private fun EmptyLibrary(
    tab: LibraryTab,
    saveHistory: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        tab == LibraryTab.Bookmarks -> {
            EmptyState(
                title = stringResource(R.string.library_bookmarks_empty_title),
                message = stringResource(R.string.library_bookmarks_empty_message),
                icon = WikiIcons.BookmarkBorder,
                modifier = modifier,
            )
        }

        saveHistory -> {
            EmptyState(
                title = stringResource(R.string.library_history_empty_title),
                message = stringResource(R.string.library_history_empty_message),
                icon = WikiIcons.History,
                modifier = modifier,
            )
        }

        else -> {
            EmptyState(
                title = stringResource(R.string.library_history_off_title),
                message = stringResource(R.string.library_history_off_message),
                icon = WikiIcons.History,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ClearHistoryDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_clear_history_dialog_title)) },
        text = { Text(stringResource(R.string.library_clear_history_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.library_clear_history_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_clear_history_cancel)) }
        },
    )
}

@get:StringRes
private val LibraryTab.label: Int
    get() =
        when (this) {
            LibraryTab.Bookmarks -> R.string.library_tab_bookmarks
            LibraryTab.History -> R.string.library_tab_history
        }

@PreviewLightDark
@Composable
private fun LibraryScreenPreview() {
    WikiDroidTheme(dynamicColor = false) {
        LibraryScreen(
            state =
                LibraryState(
                    bookmarks =
                        persistentListOf(
                            LibraryEntry("Diamond", "Diamond", null, Instant.fromEpochSeconds(1_700_000_000), true),
                            LibraryEntry("Creeper", "Creeper", null, Instant.fromEpochSeconds(1_700_100_000), false),
                        ),
                ),
            onAction = {},
        )
    }
}
