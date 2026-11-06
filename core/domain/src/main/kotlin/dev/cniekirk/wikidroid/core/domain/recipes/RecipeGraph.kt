package dev.cniekirk.wikidroid.core.domain.recipes

import dev.cniekirk.wikidroid.core.model.recipe.Ingredient
import dev.cniekirk.wikidroid.core.model.recipe.Recipe
import dev.cniekirk.wikidroid.core.model.recipe.RecipeBook

/**
 * A [RecipeBook] indexed for planning, with the default recipe for every item and the default item for every
 * ingredient.
 *
 * Defaults come from a rough cost: a raw item (or one nothing can make) costs 1, and a crafted item costs what its
 * cheapest recipe takes divided by how many it makes. Costs only ever go down, so a default never loops back on
 * itself. Ties go to the recipe named after the item (`stick` over `stick_from_bamboo_item`), then to the game's
 * order (oak before spruce).
 */
internal class RecipeGraph(
    val book: RecipeBook,
) {
    private val byResult: Map<String, List<Recipe>> = book.recipes.groupBy { it.result }
    private val byId: Map<String, Recipe> = book.recipes.associateBy { it.id }
    private val cost: Map<String, Double> = costs()
    private val defaults: Map<String, Recipe> =
        byResult.keys
            .mapNotNull { item ->
                cheapest(item)?.let { item to it }
            }.toMap()

    fun recipe(id: String): Recipe? = byId[id]

    /** Recipes for [item] the planner may use: not unpacking, and each ingredient has an option besides [item]. */
    fun usableRecipes(item: String): List<Recipe> = byResult[item].orEmpty().filter(::isUsable)

    fun isUsable(recipe: Recipe): Boolean =
        !recipe.unpacks && recipe.ingredients.all { options(recipe, it).isNotEmpty() }

    fun defaultRecipe(item: String): Recipe? = defaults[item]

    /** What [ingredient] of [recipe] can be. A recipe never takes its own result (recolouring takes another colour). */
    fun options(
        recipe: Recipe,
        ingredient: Ingredient,
    ): List<String> = ingredient.options.filter { it != recipe.result }

    fun defaultOption(
        recipe: Recipe,
        ingredient: Ingredient,
    ): String = options(recipe, ingredient).minBy(::costOf)

    private fun costOf(item: String): Double = cost[item] ?: Double.POSITIVE_INFINITY

    private fun cheapest(item: String): Recipe? =
        usableRecipes(item).minWithOrNull(
            compareBy<Recipe> { recipeCost(it) }
                .thenBy { if (it.id == item) 0 else 1 }
                .thenBy { it.id },
        )

    private fun recipeCost(recipe: Recipe): Double =
        recipe.ingredients.sumOf { ingredient -> ingredient.count * options(recipe, ingredient).minOf(::costOf) } /
            recipe.count

    private fun costs(): Map<String, Double> {
        val cost = mutableMapOf<String, Double>()
        book.items.keys.forEach { item ->
            if (item in book.rawByDefault || byResult[item].orEmpty().none(::isUsable)) cost[item] = 1.0
        }
        val crafted = book.recipes.filter { isUsable(it) && it.result !in book.rawByDefault }
        var changed = true
        var rounds = 0
        while (changed && rounds < MAX_ROUNDS) {
            changed = false
            rounds++
            crafted.forEach { recipe ->
                val candidate =
                    recipe.ingredients.sumOf { ingredient ->
                        ingredient.count * options(recipe, ingredient).minOf { cost[it] ?: Double.POSITIVE_INFINITY }
                    } / recipe.count
                if (candidate < (cost[recipe.result] ?: Double.POSITIVE_INFINITY) - EPSILON) {
                    cost[recipe.result] = candidate
                    changed = true
                }
            }
        }
        return cost
    }

    private companion object {
        const val MAX_ROUNDS = 32
        const val EPSILON = 1e-9
    }
}
