package dev.cniekirk.wikidroid.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiTopAppBar
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Category
import dev.cniekirk.wikidroid.core.ui.ArticleCard
import dev.cniekirk.wikidroid.core.ui.EmptyState
import dev.cniekirk.wikidroid.core.ui.ErrorState
import dev.cniekirk.wikidroid.core.ui.LoadingState
import dev.cniekirk.wikidroid.core.ui.PaginationEffect
import dev.cniekirk.wikidroid.core.ui.pagingFooter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import dev.cniekirk.wikidroid.core.ui.R as UiR

internal const val CATEGORY_LIST_TAG = "category-list"

/** A category's subcategory chips and page list. Stateless: the route owns the ViewModel and navigation. */
@Composable
fun CategoryDetailScreen(
    state: CategoryDetailState,
    onAction: (CategoryDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            WikiTopAppBar(
                title = state.title,
                onNavigateBack = { onAction(CategoryDetailAction.Back) },
                navigateBackDescription = stringResource(UiR.string.ui_navigate_back),
            )
        },
    ) { padding ->
        when (val phase = state.phase) {
            CategoryPhase.Loading -> {
                LoadingState(modifier = Modifier.padding(padding))
            }

            is CategoryPhase.Failed -> {
                ErrorState(
                    error = phase.error,
                    onRetry = { onAction(CategoryDetailAction.Retry) },
                    modifier = Modifier.padding(padding),
                )
            }

            CategoryPhase.Loaded -> {
                if (state.isEmpty) {
                    EmptyState(
                        title = stringResource(R.string.explore_category_empty_title),
                        message = stringResource(R.string.explore_category_empty_message),
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    CategoryMembers(state = state, onAction = onAction, contentPadding = padding)
                }
            }
        }
    }
}

@Composable
private fun CategoryMembers(
    state: CategoryDetailState,
    onAction: (CategoryDetailAction) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    // A failed batch waits for the footer's retry instead of looping.
    val canLoadMore = state.continuation != null && !state.isLoadingMore && !state.loadMoreFailed
    PaginationEffect(
        listState = listState,
        onLoadMore = { if (canLoadMore) onAction(CategoryDetailAction.LoadMore) },
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(CATEGORY_LIST_TAG),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.subcategories.isNotEmpty()) {
            item(key = "subcategories", contentType = "subcategories") {
                Subcategories(
                    subcategories = state.subcategories,
                    onClick = { onAction(CategoryDetailAction.OpenCategory(it.name)) },
                )
            }
        }
        items(state.pages, key = { it.title }, contentType = { "page" }) { page ->
            ArticleCard(article = page, onClick = { onAction(CategoryDetailAction.OpenArticle(page.title)) })
        }
        pagingFooter(
            isLoading = state.isLoadingMore,
            hasError = state.loadMoreFailed,
            onRetry = { onAction(CategoryDetailAction.LoadMore) },
        )
    }
}

@Composable
private fun Subcategories(
    subcategories: ImmutableList<Category>,
    onClick: (Category) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.explore_subcategories),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(subcategories, key = { it.name }) { category ->
                AssistChip(onClick = { onClick(category) }, label = { Text(category.displayName) })
            }
        }
    }
}

@Preview
@Composable
private fun CategoryDetailScreenPreview() {
    WikiDroidTheme(dynamicColor = false) {
        CategoryDetailScreen(
            state =
                CategoryDetailState(
                    title = "Hostile mobs",
                    phase = CategoryPhase.Loaded,
                    subcategories = persistentListOf(Category("Undead mobs"), Category("Nether mobs")),
                    pages =
                        persistentListOf(
                            ArticleSummary(title = "Creeper", description = "A hostile mob that explodes."),
                            ArticleSummary(title = "Zombie", description = "A common undead hostile mob."),
                        ),
                ),
            onAction = {},
        )
    }
}
