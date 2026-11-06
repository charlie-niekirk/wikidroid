package dev.cniekirk.wikidroid.core.testing.recipes

import dev.cniekirk.wikidroid.core.model.recipe.Ingredient
import dev.cniekirk.wikidroid.core.model.recipe.Recipe
import dev.cniekirk.wikidroid.core.model.recipe.RecipeBook
import dev.cniekirk.wikidroid.core.model.recipe.RecipeItem
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet

/** Builds a small [RecipeBook]. Every item mentioned gets a name made from its id (`oak_planks` is "Oak Planks"). */
class RecipeBookBuilder(
    private val version: String,
) {
    private val recipes = mutableListOf<Recipe>()
    private val raw = mutableSetOf<String>()

    fun raw(vararg ids: String) {
        raw += ids
    }

    fun recipe(
        id: String,
        vararg ingredients: Ingredient,
        result: String = id,
        makes: Int = 1,
        small: Boolean = true,
        unpacks: Boolean = false,
    ) {
        recipes += Recipe(id, result, makes, ingredients.toList().toImmutableList(), unpacks, small)
    }

    fun build(): RecipeBook {
        val ids = recipes.flatMap { recipe -> listOf(recipe.result) + recipe.ingredients.flatMap { it.options } } + raw
        return RecipeBook(
            minecraftVersion = version,
            items = ids.distinct().associateWith { RecipeItem(id = it, name = nameOf(it)) }.toImmutableMap(),
            recipes = recipes.toImmutableList(),
            rawByDefault = raw.toImmutableSet(),
        )
    }

    companion object {
        fun nameOf(id: String): String =
            id.split('_').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
}

fun recipeBook(
    version: String = "26.3",
    build: RecipeBookBuilder.() -> Unit,
): RecipeBook = RecipeBookBuilder(version).apply(build).build()

/** [count] of any one of [options]. */
fun items(
    count: Int,
    vararg options: String,
): Ingredient = Ingredient(count, options.toList().toImmutableList())

/** [count] of anything in the tag [tag]. */
fun tag(
    count: Int,
    tag: String,
    vararg options: String,
): Ingredient = Ingredient(count, options.toList().toImmutableList(), tag)

object TestRecipes {
    private val wools = arrayOf("white_wool", "blue_wool")

    /** A slice of the real book: wood and torches, iron storage, a beacon, wool and dyes. */
    val miniVanilla: RecipeBook =
        recipeBook {
            raw("coal", "charcoal", "glass", "iron_ingot", "iron_nugget")
            recipe("oak_planks", items(1, "oak_log"), makes = 4)
            recipe("spruce_planks", items(1, "spruce_log"), makes = 4)
            recipe("stick", tag(2, "planks", "oak_planks", "spruce_planks"), makes = 4)
            recipe("stick_from_bamboo_item", items(2, "bamboo"), result = "stick")
            recipe("torch", items(1, "coal", "charcoal"), items(1, "stick"), makes = 4)
            recipe("crafting_table", tag(4, "planks", "oak_planks", "spruce_planks"))
            recipe("wooden_pickaxe", tag(3, "planks", "oak_planks", "spruce_planks"), items(2, "stick"), small = false)
            recipe("wooden_shovel", tag(1, "planks", "oak_planks", "spruce_planks"), items(2, "stick"), small = false)
            recipe("iron_block", items(9, "iron_ingot"), small = false)
            recipe(
                "iron_ingot_from_iron_block",
                items(1, "iron_block"),
                result = "iron_ingot",
                makes = 9,
                unpacks = true,
            )
            recipe("iron_ingot_from_nuggets", items(9, "iron_nugget"), result = "iron_ingot", small = false)
            recipe("iron_nugget", items(1, "iron_ingot"), makes = 9, unpacks = true)
            recipe("beacon", items(5, "glass"), items(1, "nether_star"), items(3, "obsidian"), small = false)
            recipe("white_wool_from_string", items(4, "string"), result = "white_wool")
            recipe("dye_white_wool", items(1, "white_dye"), items(1, *wools), result = "white_wool")
            recipe("dye_blue_wool", items(1, "blue_dye"), items(1, *wools), result = "blue_wool")
            recipe("blue_dye", items(1, "lapis_lazuli"))
            recipe("white_dye", items(1, "bone_meal"))
        }
}
