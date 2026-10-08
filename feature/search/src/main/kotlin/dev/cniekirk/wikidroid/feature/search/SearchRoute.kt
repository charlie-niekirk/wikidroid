package dev.cniekirk.wikidroid.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** Connects [SearchScreen] to its ViewModel and to navigation. */
@Composable
fun SearchRoute(
    onOpenArticle: (title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = metroViewModel(),
) {
    val state by viewModel.collectAsState()

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is SearchEffect.OpenArticle -> onOpenArticle(effect.title)
        }
    }

    SearchScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}
