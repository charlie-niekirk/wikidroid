package dev.cniekirk.wikidroid.core.domain.recipes

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.map
import dev.cniekirk.wikidroid.core.data.RecipeRepository
import dev.cniekirk.wikidroid.core.model.recipe.RecipeItem
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Craftable items matching [query], best first: the exact name, then names starting with it, then names with a word
 * starting with it, then names containing it. A blank query lists every craftable item by name.
 */
@Inject
class SearchRecipeItemsUseCase(
    private val recipes: RecipeRepository,
) {
    suspend operator fun invoke(query: String): Result<ImmutableList<RecipeItem>, DataError> =
        recipes.recipeBook().map { book ->
            val needle = query.trim().lowercase()
            book.recipes
                .map { it.result }
                .distinct()
                .map(book::item)
                .mapNotNull { item -> rank(item.name.lowercase(), needle)?.let { item to it } }
                .sortedWith(compareBy({ it.second }, { it.first.name }))
                .map { it.first }
                .toImmutableList()
        }

    /** How well a name matches, best first. */
    private enum class Match { Exact, Prefix, WordPrefix, Anywhere }

    private fun rank(
        name: String,
        needle: String,
    ): Match? =
        when {
            needle.isEmpty() || name == needle -> Match.Exact
            name.startsWith(needle) -> Match.Prefix
            name.split(' ').any { it.startsWith(needle) } -> Match.WordPrefix
            needle in name -> Match.Anywhere
            else -> null
        }
}
