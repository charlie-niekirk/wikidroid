package dev.cniekirk.wikidroid.core.domain.recipes

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.DefaultDispatcher
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.map
import dev.cniekirk.wikidroid.core.data.RecipeRepository
import dev.cniekirk.wikidroid.core.model.recipe.CraftingPlan
import dev.cniekirk.wikidroid.core.model.recipe.CraftingTarget
import dev.cniekirk.wikidroid.core.model.recipe.PlanChoices
import dev.cniekirk.wikidroid.core.model.recipe.RecipeBook
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The raw materials, crafting steps and leftovers for [CraftingTarget]s, following the user's [PlanChoices]. */
@Inject
class CalculateCraftingPlanUseCase(
    private val recipes: RecipeRepository,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    // Indexing the book takes longer than a plan, and the repository hands out one book, so keep its graph.
    @Volatile private var graph: RecipeGraph? = null

    suspend operator fun invoke(
        targets: List<CraftingTarget>,
        choices: PlanChoices,
    ): Result<CraftingPlan, DataError> =
        recipes.recipeBook().map { book ->
            withContext(dispatcher) { CraftingPlanner(graphFor(book)).plan(targets, choices) }
        }

    private fun graphFor(book: RecipeBook): RecipeGraph =
        graph?.takeIf { it.book === book } ?: RecipeGraph(book).also { graph = it }
}
