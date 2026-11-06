package dev.cniekirk.wikidroid.feature.tools.calculator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.orbitmvi.orbit.compose.collectAsState

/** Connects [ResourceCalculatorScreen] to its ViewModel and to navigation. */
@Composable
fun ResourceCalculatorRoute(
    onNavigateBack: () -> Unit,
    onOpenArticle: (title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResourceCalculatorViewModel = metroViewModel(),
) {
    val state by viewModel.collectAsState()

    ResourceCalculatorScreen(
        state = state,
        onAction = { action ->
            when (action) {
                CalculatorAction.Back -> {
                    onNavigateBack()
                }

                is CalculatorAction.OpenArticle -> {
                    // Close the item sheet first, or it is still open on the way back.
                    viewModel.onAction(CalculatorAction.Sheet.Dismiss)
                    onOpenArticle(action.title)
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
        modifier = modifier,
    )
}
