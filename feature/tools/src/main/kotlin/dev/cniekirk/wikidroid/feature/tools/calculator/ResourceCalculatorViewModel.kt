package dev.cniekirk.wikidroid.feature.tools.calculator

import androidx.lifecycle.ViewModel
import dev.cniekirk.wikidroid.core.data.RecipeRepository
import dev.cniekirk.wikidroid.core.domain.recipes.CalculateCraftingPlanUseCase
import dev.cniekirk.wikidroid.core.domain.recipes.SearchRecipeItemsUseCase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * Drives the resource calculator. Every change to the targets or choices plans again; a plan that comes back after a
 * newer change is dropped (see [CalculatorState.withPlan]). The list lives here only, so it survives tab switches but
 * not the app being closed.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ResourceCalculatorViewModel(
    private val recipes: RecipeRepository,
    private val calculatePlan: CalculateCraftingPlanUseCase,
    private val searchItems: SearchRecipeItemsUseCase,
) : ViewModel(),
    OrbitContainerHost<CalculatorState, CalculatorState, Nothing> {
    override val container =
        orbitContainer<CalculatorState, Nothing>(CalculatorState()) { loadBook() }

    fun onAction(action: CalculatorAction) {
        when (action) {
            is CalculatorAction.Target -> replan { it.withTargetAction(action) }

            is CalculatorAction.Choice -> replan { it.withChoiceAction(action) }

            is CalculatorAction.Sheet -> intent { reduce { state.withSheetAction(action) } }

            is CalculatorAction.QueryChanged -> search(action.query)

            CalculatorAction.Retry -> loadBook()

            // Navigation is the route's job.
            CalculatorAction.Back, is CalculatorAction.OpenArticle -> Unit
        }
    }

    /** Loads the recipe book when the screen opens, and again on retry. */
    private fun loadBook() =
        intent {
            val result = recipes.recipeBook()
            reduce { state.withBook(result) }
        }

    private fun replan(change: (CalculatorState) -> CalculatorState) =
        intent {
            reduce { change(state) }
            val request = state.request
            val result = calculatePlan(request.targets, request.choices)
            reduce { state.withPlan(request, result) }
        }

    private fun search(query: String) =
        intent {
            reduce { state.copy(searchQuery = query) }
            val result = searchItems(query)
            reduce { state.withSearchResults(query, result) }
        }
}
