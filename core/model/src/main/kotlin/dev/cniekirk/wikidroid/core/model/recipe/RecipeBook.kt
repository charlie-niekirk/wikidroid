package dev.cniekirk.wikidroid.core.model.recipe

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/** An item a recipe makes or takes. [id] is the game's id without `minecraft:` (`oak_planks`). */
@Immutable
data class RecipeItem(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
)

/**
 * [count] of any one of [options] (item ids, in the game's order). [tag] names the item tag the options came from
 * (`planks`), if any.
 */
@Immutable
data class Ingredient(
    val count: Int,
    val options: ImmutableList<String>,
    val tag: String? = null,
) {
    /**
     * Identifies the choice between [options]. Ingredients with the same options share it, so picking spruce for
     * "any planks" applies wherever any planks will do.
     */
    val key: String get() = tag?.let { "#$it" } ?: options.joinToString(",")
}

/**
 * A crafting recipe: [ingredients] make [count] of [result]. [unpacks] marks storage unpacking (a block into nine
 * ingots), which is never picked unless asked for. [small] recipes fit the 2x2 inventory grid.
 */
@Immutable
data class Recipe(
    val id: String,
    val result: String,
    val count: Int,
    val ingredients: ImmutableList<Ingredient>,
    val unpacks: Boolean = false,
    val small: Boolean = true,
)

/**
 * Every crafting recipe of one Minecraft: Java Edition version. Items in [rawByDefault] (ingots, glass, gems: things a
 * furnace makes or players gather) are not broken down unless the user asks.
 */
@Immutable
data class RecipeBook(
    val minecraftVersion: String,
    val items: ImmutableMap<String, RecipeItem>,
    val recipes: ImmutableList<Recipe>,
    val rawByDefault: ImmutableSet<String>,
) {
    fun item(id: String): RecipeItem = items[id] ?: RecipeItem(id = id, name = id)
}
