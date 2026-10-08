package dev.cniekirk.wikidroid.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.orbitmvi.orbit.compose.collectAsState

/** Connects [SettingsScreen] to its ViewModel and to navigation. */
@Composable
fun SettingsRoute(
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = metroViewModel(),
) {
    val state by viewModel.collectAsState()

    SettingsScreen(
        state = state,
        onAction = { action ->
            when (action) {
                SettingsAction.OpenAbout -> onOpenAbout()
                else -> viewModel.onAction(action)
            }
        },
        modifier = modifier,
    )
}
