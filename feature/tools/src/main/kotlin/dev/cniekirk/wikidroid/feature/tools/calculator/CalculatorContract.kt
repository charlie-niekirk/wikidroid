package dev.cniekirk.wikidroid.feature.tools.calculator

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.model.recipe.CraftingPlan
import dev.cniekirk.wikidroid.core.model.recipe.CraftingStep
import dev.cniekirk.wikidroid.core.model.recipe.CraftingTarget
import dev.cniekirk.wikidroid.core.model.recipe.PlanChoices
import dev.cniekirk.wikidroid.core.model.recipe.RawMaterial
import dev.cniekirk.wikidroid.core.model.recipe.RecipeBook
import dev.cniekirk.wikidroid.core.model.recipe.RecipeItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet

/** [quantity] of [item] on the list of things to make. */
@Immutable
data class CalculatorTarget(
    val item: RecipeItem,
    val quantity: Int,
)

/** The bottom sheet on show, if any. */
sealed interface CalculatorSheet {
    /** Search for an item to add. */
    data object Picker : CalculatorSheet

    /** What to do with one item in the plan: stop at it, craft it, pick its recipe, read about it. */
    data class Item(
        val itemId: String,
    ) : CalculatorSheet

    /** Which item to use for an ingredient that several would do for (any planks). */
    data class Variants(
        val key: String,
        val options: ImmutableList<RecipeItem>,
        val selected: String,
    ) : CalculatorSheet
}

/** What a plan is worked out from; a plan for any other request is out of date. */
@Immutable
data class PlanRequest(
    val targets: ImmutableList<CraftingTarget>,
    val choices: PlanChoices,
)

@Immutable
data class CalculatorState(
    val minecraftVersion: String? = null,
    val loadError: DataError? = null,
    val targets: ImmutableList<CalculatorTarget> = persistentListOf(),
    val choices: PlanChoices = PlanChoices(),
    val plan: CraftingPlan = CraftingPlan(),
    val planError: DataError? = null,
    val searchQuery: String = "",
    val searchResults: ImmutableList<RecipeItem>? = null,
    val sheet: CalculatorSheet? = null,
) {
    val request: PlanRequest
        get() = PlanRequest(targets.map { CraftingTarget(it.item.id, it.quantity) }.toImmutableList(), choices)

    /** The step that makes [itemId], if the plan crafts it. */
    fun stepFor(itemId: String): CraftingStep? = plan.steps.firstOrNull { it.output.item.id == itemId }

    /** [itemId] as a raw material, if the plan gathers it. */
    fun rawFor(itemId: String): RawMaterial? = plan.raw.firstOrNull { it.item.id == itemId }

    fun withBook(result: Result<RecipeBook, DataError>): CalculatorState =
        when (result) {
            is Result.Success -> copy(minecraftVersion = result.data.minecraftVersion, loadError = null)
            is Result.Failure -> copy(loadError = result.error)
        }

    fun withTargetAction(action: CalculatorAction.Target): CalculatorState =
        when (action) {
            is CalculatorAction.Target.Add -> {
                val existing = targets.firstOrNull { it.item.id == action.item.id }
                val added =
                    if (existing == null) {
                        targets + CalculatorTarget(action.item, 1)
                    } else {
                        targets.map { if (it == existing) it.copy(quantity = clamp(it.quantity + 1)) else it }
                    }
                copy(targets = added.toImmutableList(), sheet = null)
            }

            is CalculatorAction.Target.SetQuantity -> {
                copy(
                    targets =
                        targets
                            .map { if (it.item.id == action.itemId) it.copy(quantity = clamp(action.quantity)) else it }
                            .toImmutableList(),
                )
            }

            is CalculatorAction.Target.Remove -> {
                copy(targets = targets.filterNot { it.item.id == action.itemId }.toImmutableList())
            }
        }

    /** A choice closes the sheet it was made in. */
    fun withChoiceAction(action: CalculatorAction.Choice): CalculatorState {
        val choices =
            when (action) {
                is CalculatorAction.Choice.Own -> {
                    choices.copy(
                        owned = (choices.owned + action.itemId).toImmutableSet(),
                        craft = (choices.craft - action.itemId).toImmutableSet(),
                    )
                }

                is CalculatorAction.Choice.Craft -> {
                    choices.copy(
                        owned = (choices.owned - action.itemId).toImmutableSet(),
                        craft = (choices.craft + action.itemId).toImmutableSet(),
                    )
                }

                is CalculatorAction.Choice.ChooseRecipe -> {
                    choices.copy(recipes = (choices.recipes + (action.itemId to action.recipeId)).toImmutableMap())
                }

                is CalculatorAction.Choice.ChooseVariant -> {
                    choices.copy(variants = (choices.variants + (action.key to action.itemId)).toImmutableMap())
                }

                CalculatorAction.Choice.Reset -> {
                    PlanChoices()
                }
            }
        return copy(choices = choices, sheet = null)
    }

    fun withSheetAction(action: CalculatorAction.Sheet): CalculatorState =
        when (action) {
            CalculatorAction.Sheet.OpenPicker -> {
                copy(sheet = CalculatorSheet.Picker)
            }

            is CalculatorAction.Sheet.ShowItem -> {
                copy(sheet = CalculatorSheet.Item(action.itemId))
            }

            is CalculatorAction.Sheet.ShowVariants -> {
                copy(
                    sheet = CalculatorSheet.Variants(action.key, action.options, action.selected),
                )
            }

            CalculatorAction.Sheet.Dismiss -> {
                copy(sheet = null)
            }
        }

    /** Applies a plan worked out for [request], unless the targets or choices have changed since. */
    fun withPlan(
        request: PlanRequest,
        result: Result<CraftingPlan, DataError>,
    ): CalculatorState =
        when {
            request != this.request -> this
            result is Result.Success -> copy(plan = result.data, planError = null)
            else -> copy(planError = (result as Result.Failure).error)
        }

    /** Applies results for [query], unless the user has typed something else since. */
    fun withSearchResults(
        query: String,
        result: Result<ImmutableList<RecipeItem>, DataError>,
    ): CalculatorState =
        when {
            query != searchQuery -> this
            result is Result.Success -> copy(searchResults = result.data)
            else -> copy(searchResults = persistentListOf())
        }

    companion object {
        const val MAX_QUANTITY = 9_999

        fun clamp(quantity: Int): Int = quantity.coerceIn(1, MAX_QUANTITY)
    }
}

sealed interface CalculatorAction {
    /** The top bar's back arrow; the route handles it. */
    data object Back : CalculatorAction

    /** Opens [title]'s wiki article; the route handles it. */
    data class OpenArticle(
        val title: String,
    ) : CalculatorAction

    /** Loads the recipes again after they failed to load. */
    data object Retry : CalculatorAction

    data class QueryChanged(
        val query: String,
    ) : CalculatorAction

    /** Changes to the list of things to make. Each one plans again. */
    sealed interface Target : CalculatorAction {
        data class Add(
            val item: RecipeItem,
        ) : Target

        data class SetQuantity(
            val itemId: String,
            val quantity: Int,
        ) : Target

        data class Remove(
            val itemId: String,
        ) : Target
    }

    /** Changes to how things are made. Each one plans again. */
    sealed interface Choice : CalculatorAction {
        /** Stop at [itemId]: the user has it, or gets it some other way. */
        data class Own(
            val itemId: String,
        ) : Choice

        /** Craft [itemId], even if it is raw by default. */
        data class Craft(
            val itemId: String,
        ) : Choice

        data class ChooseRecipe(
            val itemId: String,
            val recipeId: String,
        ) : Choice

        data class ChooseVariant(
            val key: String,
            val itemId: String,
        ) : Choice

        /** Back to the default recipes, variants and raw materials. */
        data object Reset : Choice
    }

    sealed interface Sheet : CalculatorAction {
        data object OpenPicker : Sheet

        data class ShowItem(
            val itemId: String,
        ) : Sheet

        data class ShowVariants(
            val key: String,
            val options: ImmutableList<RecipeItem>,
            val selected: String,
        ) : Sheet

        data object Dismiss : Sheet
    }
}
