package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.cniekirk.wikidroid.core.seedmap.TileCache
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** Connects [SeedMapScreen] to its ViewModel and to navigation, and moves the map when the ViewModel asks. */
@Composable
fun SeedMapRoute(
    tiles: TileCache,
    onOpenArticle: (title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeedMapViewModel = metroViewModel(),
) {
    val state by viewModel.collectAsState()
    val camera = rememberMapCamera()

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is SeedMapEffect.OpenArticle -> onOpenArticle(effect.title)
            is SeedMapEffect.CenterOn -> camera.centerOn(effect.pos)
        }
    }

    SeedMapScreen(state = state, tiles = tiles, onAction = viewModel::onAction, modifier = modifier, camera = camera)
}
