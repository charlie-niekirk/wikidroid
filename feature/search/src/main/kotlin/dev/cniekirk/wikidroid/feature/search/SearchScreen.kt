package dev.cniekirk.wikidroid.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIconButton
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.ui.ArticleCard
import dev.cniekirk.wikidroid.core.ui.EmptyState
import dev.cniekirk.wikidroid.core.ui.ErrorState
import dev.cniekirk.wikidroid.core.ui.LoadingState
import dev.cniekirk.wikidroid.core.ui.MessageState
import dev.cniekirk.wikidroid.core.ui.PageThumbnail
import dev.cniekirk.wikidroid.core.ui.PaginationEffect
import dev.cniekirk.wikidroid.core.ui.pagingFooter
import dev.cniekirk.wikidroid.core.ui.userMessage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import dev.cniekirk.wikidroid.core.ui.R as UiR

internal const val SEARCH_FIELD_TAG = "search-field"
internal const val RESULTS_LIST_TAG = "search-results"

/**
 * The Search tab. Stateless: the route owns the ViewModel and navigation.
 *
 * The text lives in a [androidx.compose.foundation.text.input.TextFieldState] here rather than in [state],
 * because round-tripping every keystroke through the ViewModel can drop characters typed in the meantime.
 */
@Composable
fun SearchScreen(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textState = rememberTextFieldState(initialText = state.query)
    val currentOnAction by rememberUpdatedState(onAction)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }.collect { currentOnAction(SearchAction.QueryChanged(it)) }
    }

    val submit = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onAction(SearchAction.Submit)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            SearchField(
                state = textState,
                onSubmit = submit,
                onClear = { textState.clearText() },
            )
        },
    ) { padding ->
        when {
            state.results !is ResultsState.Idle -> {
                ResultsContent(results = state.results, onAction = onAction, contentPadding = padding)
            }

            state.query.isBlank() -> {
                RecentSearches(
                    recent = state.recentSearches,
                    contentPadding = padding,
                    onSelect = { query ->
                        textState.setTextAndPlaceCursorAtEnd(query)
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onAction(SearchAction.SelectRecent(query))
                    },
                    onRemove = { onAction(SearchAction.RemoveRecent(it)) },
                    onClearAll = { onAction(SearchAction.ClearRecents) },
                )
            }

            else -> {
                Suggestions(
                    query = state.query.trim(),
                    suggestions = state.suggestions,
                    contentPadding = padding,
                    onSearch = submit,
                    onSuggestionClick = { onAction(SearchAction.SuggestionClicked(it)) },
                    onRetry = { onAction(SearchAction.RetrySuggestions) },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    state: TextFieldState,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        TextField(
            state = state,
            modifier = Modifier.fillMaxWidth().testTag(SEARCH_FIELD_TAG),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { WikiIcon(icon = WikiIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (state.text.isNotEmpty()) {
                    WikiIconButton(
                        icon = WikiIcons.Close,
                        contentDescription = stringResource(R.string.search_clear_query),
                        onClick = onClear,
                    )
                }
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = KeyboardActionHandler { onSubmit() },
            shape = RoundedCornerShape(28.dp),
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                ),
        )
    }
}

@Composable
private fun RecentSearches(
    recent: ImmutableList<String>,
    contentPadding: PaddingValues,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    if (recent.isEmpty()) {
        MessageState(
            icon = WikiIcons.Search,
            title = stringResource(R.string.search_prompt_title),
            message = stringResource(R.string.search_prompt_message),
            modifier = Modifier.padding(contentPadding),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "recent-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_recent_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClearAll) { Text(stringResource(R.string.search_recent_clear_all)) }
            }
        }
        items(recent, key = { it }) { query ->
            ListItem(
                headlineContent = { Text(query) },
                leadingContent = { WikiIcon(icon = WikiIcons.History, contentDescription = null) },
                trailingContent = {
                    WikiIconButton(
                        icon = WikiIcons.Close,
                        contentDescription = stringResource(R.string.search_recent_remove, query),
                        onClick = { onRemove(query) },
                    )
                },
                modifier = Modifier.clickable { onSelect(query) },
            )
        }
    }
}

@Composable
private fun Suggestions(
    query: String,
    suggestions: SuggestionsState,
    contentPadding: PaddingValues,
    onSearch: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "search-for") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.search_for_query, query)) },
                leadingContent = { WikiIcon(icon = WikiIcons.Search, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onSearch),
            )
            HorizontalDivider()
        }
        when (suggestions) {
            SuggestionsState.Idle, SuggestionsState.Loading -> {
                Unit
            }

            is SuggestionsState.Loaded -> {
                items(suggestions.items, key = { it.title }) { page ->
                    ListItem(
                        headlineContent = { Text(page.displayTitle, maxLines = 1) },
                        supportingContent =
                            page.description?.let { description ->
                                { Text(description, maxLines = 1) }
                            },
                        leadingContent = { PageThumbnail(url = page.thumbnailUrl, size = SuggestionThumbnailSize) },
                        modifier = Modifier.clickable { onSuggestionClick(page.title) },
                    )
                }
            }

            is SuggestionsState.Failed -> {
                item(key = "suggestions-failed") {
                    SuggestionsError(error = suggestions.error, onRetry = onRetry)
                }
            }
        }
    }
}

private val SuggestionThumbnailSize = 40.dp

@Composable
private fun SuggestionsError(
    error: DataError,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.search_suggestions_failed), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = error.userMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRetry) { Text(stringResource(UiR.string.ui_retry)) }
    }
}

@Composable
private fun ResultsContent(
    results: ResultsState,
    onAction: (SearchAction) -> Unit,
    contentPadding: PaddingValues,
) {
    when (results) {
        ResultsState.Idle -> {
            Unit
        }

        is ResultsState.Loading -> {
            LoadingState(modifier = Modifier.padding(contentPadding))
        }

        is ResultsState.Failed -> {
            ErrorState(
                error = results.error,
                onRetry = { onAction(SearchAction.RetryResults) },
                modifier = Modifier.padding(contentPadding),
            )
        }

        is ResultsState.Loaded -> {
            if (results.items.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.search_no_results_title),
                    message = stringResource(R.string.search_no_results_message, results.query),
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                ResultsList(results = results, onAction = onAction, contentPadding = contentPadding)
            }
        }
    }
}

@Composable
private fun ResultsList(
    results: ResultsState.Loaded,
    onAction: (SearchAction) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    // A failed page waits for the footer's retry instead of looping.
    val canLoadMore = results.continuation != null && !results.isLoadingMore && !results.loadMoreFailed
    PaginationEffect(
        listState = listState,
        onLoadMore = { if (canLoadMore) onAction(SearchAction.LoadMore) },
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(RESULTS_LIST_TAG),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(results.items, key = { it.title }, contentType = { "result" }) { page ->
            ArticleCard(article = page, onClick = { onAction(SearchAction.ResultClicked(page.title)) })
        }
        pagingFooter(
            isLoading = results.isLoadingMore,
            hasError = results.loadMoreFailed,
            onRetry = { onAction(SearchAction.LoadMore) },
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreview() {
    WikiDroidTheme(dynamicColor = false) {
        SearchScreen(
            state =
                SearchState(
                    query = "cree",
                    suggestions =
                        SuggestionsState.Loaded(
                            persistentListOf(
                                ArticleSummary(title = "Creeper", description = "A hostile mob that explodes."),
                                ArticleSummary(title = "Creeper Spawn Egg"),
                            ),
                        ),
                ),
            onAction = {},
        )
    }
}
