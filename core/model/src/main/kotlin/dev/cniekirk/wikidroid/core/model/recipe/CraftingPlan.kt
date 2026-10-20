package dev.cniekirk.wikidroid.core.model.recipe

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/** [quantity] of [itemId] the user wants to make. */
@Immutable
data class CraftingTarget(
    val itemId: String,
    val quantity: Int,
)

/**
 * How the user wants things made, on top of the defaults.
 *
 * @property owned items not to break down (the user has them, or will get them some other way).
 * @property craft items that are raw by default but should be crafted.
 * @property recipes the recipe id to use for an item.
 * @property variants the item to use for an ingredient, by [Ingredient.key].
 */
@Immutable
data class PlanChoices(
    val owned: ImmutableSet<String> = persistentSetOf(),
    val craft: ImmutableSet<String> = persistentSetOf(),
    val recipes: ImmutableMap<String, String> = persistentMapOf(),
    val variants: ImmutableMap<String, String> = persistentMapOf(),
) {
    val isEmpty: Boolean get() = owned.isEmpty() && craft.isEmpty() && recipes.isEmpty() && variants.isEmpty()
}

@Immutable
data class ItemCount(
    val item: RecipeItem,
    val count: Long,
)

/** A material to gather. [owned] when the user chose to stop at it; [craftable] when a recipe could make it. */
@Immutable
data class RawMaterial(
    val item: RecipeItem,
    val count: Long,
    val owned: Boolean,
    val craftable: Boolean,
)

/** [count] of [item] going into a step. [options] lists what else would do (empty when nothing else would). */
@Immutable
data class StepInput(
    val item: RecipeItem,
    val count: Long,
    val variantKey: String?,
    val options: ImmutableList<RecipeItem>,
)

/** A recipe that could make a step's output: one craft takes [inputs] and makes [makes]. */
@Immutable
data class RecipeOption(
    val recipeId: String,
    val makes: Int,
    val inputs: ImmutableList<ItemCount>,
    val chosen: Boolean,
)

/** Craft [recipeId] [crafts] times, taking [inputs] and making [output]. [options] includes the chosen recipe. */
@Immutable
data class CraftingStep(
    val recipeId: String,
    val crafts: Long,
    val output: ItemCount,
    val inputs: ImmutableList<StepInput>,
    val options: ImmutableList<RecipeOption>,
    val needsCraftingTable: Boolean,
)

/**
 * What it takes to make some targets: [raw] materials to gather, [steps] in an order that works (every step's inputs
 * are raw or made by an earlier step), and what is [leftovers] once batches are rounded up. [ignoredChoices] lists
 * items whose recipe or variant choice was dropped because it would need the item itself.
 */
@Immutable
data class CraftingPlan(
    val raw: ImmutableList<RawMaterial> = persistentListOf(),
    val steps: ImmutableList<CraftingStep> = persistentListOf(),
    val leftovers: ImmutableList<ItemCount> = persistentListOf(),
    val ignoredChoices: ImmutableSet<String> = persistentSetOf(),
)
