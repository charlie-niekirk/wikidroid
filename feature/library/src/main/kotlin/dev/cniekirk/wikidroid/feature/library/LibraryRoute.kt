package dev.cniekirk.wikidroid.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** Connects [LibraryScreen] to its ViewModel and to navigation. */
@Composable
fun LibraryRoute(
    onOpenArticle: (title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = metroViewModel(),
) {
    val state by viewModel.collectAsState()

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is LibraryEffect.OpenArticle -> onOpenArticle(effect.title)
        }
    }

    LibraryScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}
