package dev.cniekirk.wikidroid.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Calls [onLoadMore] when the last [buffer] items of [listState] come into view, and again each time the list
 * grows while the end is still in view. The caller decides whether there is more to load; this only reports
 * that the user got near the end, so it is safe for [onLoadMore] to be a no-op.
 */
@Composable
fun PaginationEffect(
    listState: LazyListState,
    onLoadMore: () -> Unit,
    buffer: Int = DEFAULT_PAGINATION_BUFFER,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, buffer) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            // The item count is part of the value, so growing the list while the end is in view fires again.
            if (info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 1 - buffer) {
                info.totalItemsCount
            } else {
                -1
            }
        }.distinctUntilChanged()
            .filter { it >= 0 }
            .collect { currentOnLoadMore() }
    }
}

private const val DEFAULT_PAGINATION_BUFFER = 4

/** The last row of a paged list: a spinner while [isLoading], or a retry prompt when the last load [hasError]. */
fun LazyListScope.pagingFooter(
    isLoading: Boolean,
    hasError: Boolean,
    onRetry: () -> Unit,
) {
    if (!isLoading && !hasError) return
    item(key = "paging-footer", contentType = "paging-footer") {
        if (isLoading) {
            val description = stringResource(R.string.ui_loading_more)
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp).semantics { contentDescription = description },
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ui_load_more_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.ui_retry)) }
            }
        }
    }
}
