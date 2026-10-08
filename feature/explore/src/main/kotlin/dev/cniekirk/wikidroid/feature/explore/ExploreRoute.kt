package dev.cniekirk.wikidroid.feature.explore

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** Connects [ExploreScreen] to its ViewModel and to navigation. */
@Composable
fun ExploreRoute(
    onOpenArticle: (title: String) -> Unit,
    onOpenCategory: (name: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = metroViewModel(),
) {
    val state by viewModel.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val randomFailedMessage = stringResource(R.string.explore_random_failed)

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is ExploreEffect.OpenArticle -> onOpenArticle(effect.title)
            ExploreEffect.RandomArticleFailed -> snackbarHostState.showSnackbar(randomFailedMessage)
        }
    }

    ExploreScreen(
        state = state,
        onAction = { action ->
            when (action) {
                is ExploreAction.OpenArticle -> onOpenArticle(action.title)
                is ExploreAction.OpenCategory -> onOpenCategory(action.name)
                else -> viewModel.onAction(action)
            }
        },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** Connects [CategoryDetailScreen] to a ViewModel created for [title] and to navigation. */
@Composable
fun CategoryDetailRoute(
    title: String,
    onOpenArticle: (title: String) -> Unit,
    onOpenCategory: (name: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        assistedMetroViewModel<CategoryDetailViewModel, CategoryDetailViewModel.Factory> { create(title) }
    val state by viewModel.collectAsState()

    CategoryDetailScreen(
        state = state,
        onAction = { action ->
            when (action) {
                is CategoryDetailAction.OpenArticle -> onOpenArticle(action.title)
                is CategoryDetailAction.OpenCategory -> onOpenCategory(action.name)
                CategoryDetailAction.Back -> onNavigateBack()
                else -> viewModel.onAction(action)
            }
        },
        modifier = modifier,
    )
}
