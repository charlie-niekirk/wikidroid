package dev.cniekirk.wikidroid.core.domain.recipes

import dev.cniekirk.wikidroid.core.model.recipe.CraftingPlan
import dev.cniekirk.wikidroid.core.model.recipe.CraftingStep
import dev.cniekirk.wikidroid.core.model.recipe.CraftingTarget
import dev.cniekirk.wikidroid.core.model.recipe.Ingredient
import dev.cniekirk.wikidroid.core.model.recipe.ItemCount
import dev.cniekirk.wikidroid.core.model.recipe.PlanChoices
import dev.cniekirk.wikidroid.core.model.recipe.RawMaterial
import dev.cniekirk.wikidroid.core.model.recipe.Recipe
import dev.cniekirk.wikidroid.core.model.recipe.RecipeOption
import dev.cniekirk.wikidroid.core.model.recipe.StepInput
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * Works out what it takes to make some targets.
 *
 * First every item gets a recipe (the user's choice, else the default) or is left raw, walking down from the targets.
 * Then demand flows down in an order where each item is handled only after everything that consumes it, so an
 * intermediate shared by several targets is crafted in one batch: ten sticks for torches and a pickaxe are three
 * crafts, not four.
 */
internal class CraftingPlanner(
    private val graph: RecipeGraph,
) {
    fun plan(
        targets: List<CraftingTarget>,
        choices: PlanChoices,
    ): CraftingPlan {
        val wanted =
            targets
                .groupBy { it.itemId }
                .mapValues { (_, same) -> same.sumOf { it.quantity.toLong() }.coerceIn(1L, MAX_QUANTITY) }
        if (wanted.isEmpty()) return CraftingPlan()
        val resolution = Resolution(choices)
        wanted.keys.forEach { resolution.resolve(it, emptySet()) }
        val demand = distribute(resolution.nodes, wanted)
        return toPlan(resolution, demand, choices)
    }

    /** An item's recipe and the item picked for each ingredient; a null [recipe] means the item is raw. */
    private class Node(
        val recipe: Recipe?,
        val inputs: List<Pair<Ingredient, String>>,
        val height: Int,
    )

    private inner class Resolution(
        private val choices: PlanChoices,
    ) {
        val nodes = LinkedHashMap<String, Node>()
        val ignored = mutableSetOf<String>()

        fun resolve(
            item: String,
            onStack: Set<String>,
        ) {
            if (item in nodes) return
            val stack = onStack + item
            val node =
                candidates(item).firstNotNullOfOrNull { (recipe, useChoices) ->
                    attempt(recipe, useChoices, stack)
                }
            nodes[item] = node ?: Node(recipe = null, inputs = emptyList(), height = 0)
        }

        /** The recipes to try in order, each with whether to honour the user's variant choices. */
        private fun candidates(item: String): List<Pair<Recipe, Boolean>> {
            if (isRaw(item)) return emptyList()
            val chosen = choices.recipes[item]?.let(graph::recipe)?.takeIf { it.result == item && graph.isUsable(it) }
            val fallback = graph.defaultRecipe(item)
            return listOfNotNull(chosen?.to(true), fallback?.to(true), fallback?.to(false)).distinct()
        }

        private fun isRaw(item: String): Boolean =
            item in choices.owned ||
                (item in graph.book.rawByDefault && item !in choices.craft) ||
                graph.defaultRecipe(item) == null

        /** [recipe] with its inputs resolved, or null when an input would need an item still being worked out. */
        private fun attempt(
            recipe: Recipe,
            useChoices: Boolean,
            stack: Set<String>,
        ): Node? {
            val inputs = recipe.ingredients.map { it to pick(recipe, it, useChoices) }
            if (inputs.any { (_, item) -> item in stack }) {
                if (useChoices && usesChoices(recipe)) ignored += recipe.result
                return null
            }
            inputs.forEach { (_, item) -> resolve(item, stack) }
            val height = 1 + inputs.maxOf { (_, item) -> nodes.getValue(item).height }
            return Node(recipe, inputs, height)
        }

        private fun usesChoices(recipe: Recipe): Boolean =
            choices.recipes[recipe.result] == recipe.id || recipe.ingredients.any { it.key in choices.variants }

        private fun pick(
            recipe: Recipe,
            ingredient: Ingredient,
            useChoices: Boolean,
        ): String {
            val chosen =
                choices.variants[ingredient.key]?.takeIf {
                    useChoices &&
                        it in graph.options(recipe, ingredient)
                }
            return chosen ?: graph.defaultOption(recipe, ingredient)
        }
    }

    /** How many of each item is needed, handling an item only once everything that consumes it has asked. */
    private fun distribute(
        nodes: Map<String, Node>,
        wanted: Map<String, Long>,
    ): Map<String, Long> {
        val consumers = nodes.keys.associateWith { 0 }.toMutableMap()
        nodes.values.forEach { node ->
            node.inputs.map { it.second }.distinct().forEach {
                consumers[it] =
                    consumers.getValue(it) + 1
            }
        }
        val need = wanted.toMutableMap()
        val ready = ArrayDeque(nodes.keys.filter { consumers.getValue(it) == 0 })
        while (ready.isNotEmpty()) {
            val item = ready.removeFirst()
            val node = nodes.getValue(item)
            val recipe = node.recipe ?: continue
            val crafts = ceilDiv(need[item] ?: 0L, recipe.count.toLong())
            node.inputs.forEach { (ingredient, input) -> need[input] = (need[input] ?: 0L) + crafts * ingredient.count }
            node.inputs.map { it.second }.distinct().forEach { input ->
                consumers[input] = consumers.getValue(input) - 1
                if (consumers.getValue(input) == 0) ready.addLast(input)
            }
        }
        return need
    }

    private fun toPlan(
        resolution: Resolution,
        need: Map<String, Long>,
        choices: PlanChoices,
    ): CraftingPlan {
        val book = graph.book
        val (crafted, raw) = resolution.nodes.entries.partition { it.value.recipe != null }
        val steps =
            crafted
                .sortedWith(compareBy({ it.value.height }, { book.item(it.key).name }))
                .map { (item, node) -> step(item, node, need[item] ?: 0L, choices) }
        return CraftingPlan(
            raw =
                raw
                    .map { (item, _) ->
                        RawMaterial(
                            item = book.item(item),
                            count = need[item] ?: 0L,
                            owned = item in choices.owned,
                            craftable = graph.defaultRecipe(item) != null,
                        )
                    }.sortedWith(compareByDescending<RawMaterial> { it.count }.thenBy { it.item.name })
                    .toImmutableList(),
            steps = steps.toImmutableList(),
            leftovers =
                steps
                    .map { ItemCount(it.output.item, it.output.count - (need[it.output.item.id] ?: 0L)) }
                    .filter { it.count > 0 }
                    .toImmutableList(),
            ignoredChoices = resolution.ignored.toImmutableSet(),
        )
    }

    private fun step(
        item: String,
        node: Node,
        need: Long,
        choices: PlanChoices,
    ): CraftingStep {
        val recipe = checkNotNull(node.recipe)
        val crafts = ceilDiv(need, recipe.count.toLong())
        val inputs =
            node.inputs
                .groupBy({ it.second }, { it.first })
                .map { (input, ingredients) ->
                    val ingredient = ingredients.first()
                    val options = graph.options(recipe, ingredient)
                    StepInput(
                        item = graph.book.item(input),
                        count = crafts * ingredients.sumOf { it.count },
                        variantKey = ingredient.key.takeIf { options.size > 1 },
                        options =
                            (
                                if (options.size >
                                    1
                                ) {
                                    options.map(graph.book::item)
                                } else {
                                    emptyList()
                                }
                            ).toImmutableList(),
                    )
                }
        return CraftingStep(
            recipeId = recipe.id,
            crafts = crafts,
            output = ItemCount(graph.book.item(item), crafts * recipe.count),
            inputs = inputs.toImmutableList(),
            options = graph.usableRecipes(item).map { option(it, recipe, choices) }.toImmutableList(),
            needsCraftingTable = !recipe.small,
        )
    }

    /** One craft of [recipe], showing the items the user's variant choices (or the defaults) would put in it. */
    private fun option(
        recipe: Recipe,
        chosen: Recipe,
        choices: PlanChoices,
    ): RecipeOption =
        RecipeOption(
            recipeId = recipe.id,
            makes = recipe.count,
            inputs =
                recipe.ingredients
                    .map { ingredient ->
                        val pick =
                            choices.variants[ingredient.key]?.takeIf { it in graph.options(recipe, ingredient) }
                                ?: graph.defaultOption(recipe, ingredient)
                        ItemCount(graph.book.item(pick), ingredient.count.toLong())
                    }.toImmutableList(),
            chosen = recipe.id == chosen.id,
        )

    private fun ceilDiv(
        a: Long,
        b: Long,
    ): Long = (a + b - 1) / b

    companion object {
        const val MAX_QUANTITY = 9_999L
    }
}
