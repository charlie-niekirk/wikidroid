package dev.cniekirk.wikidroid.core.testing.fake

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.data.RecipeRepository
import dev.cniekirk.wikidroid.core.model.recipe.RecipeBook
import dev.cniekirk.wikidroid.core.testing.recipes.TestRecipes

class FakeRecipeRepository(
    book: RecipeBook = TestRecipes.miniVanilla,
) : RecipeRepository {
    var bookResult: Result<RecipeBook, DataError> = Result.Success(book)

    var calls = 0
        private set

    override suspend fun recipeBook(): Result<RecipeBook, DataError> {
        calls++
        return bookResult
    }
}
