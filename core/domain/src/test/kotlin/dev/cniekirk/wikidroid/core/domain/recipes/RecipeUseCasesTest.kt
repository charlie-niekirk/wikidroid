package dev.cniekirk.wikidroid.core.domain.recipes

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.getOrNull
import dev.cniekirk.wikidroid.core.model.recipe.CraftingTarget
import dev.cniekirk.wikidroid.core.model.recipe.PlanChoices
import dev.cniekirk.wikidroid.core.testing.fake.FakeRecipeRepository
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecipeUseCasesTest {
    private val recipes = FakeRecipeRepository()
    private val calculate = CalculateCraftingPlanUseCase(recipes, UnconfinedTestDispatcher())
    private val search = SearchRecipeItemsUseCase(recipes)

    @Test
    fun calculatePlansFromTheRepositorysBook() =
        runTest {
            val plan = calculate(listOf(CraftingTarget("beacon", 1)), PlanChoices()).getOrNull()

            assertThat(
                plan
                    ?.steps
                    ?.single()
                    ?.output
                    ?.item
                    ?.id,
            ).isEqualTo("beacon")
        }

    @Test
    fun calculatePassesAFailureOn() =
        runTest {
            recipes.bookResult = Result.Failure(DataError.Parse)

            assertThat(calculate(listOf(CraftingTarget("beacon", 1)), PlanChoices()))
                .isEqualTo(Result.Failure(DataError.Parse))
        }

    @Test
    fun searchRanksExactThenPrefixThenWordThenAnywhere() =
        runTest {
            val names = search("wool").getOrNull()?.map { it.name }

            assertThat(names).containsExactly("Blue Wool", "White Wool").inOrder()
            assertThat(search("stick").getOrNull()?.first()?.name).isEqualTo("Stick")
            assertThat(search("pla").getOrNull()?.map { it.name }).containsExactly("Oak Planks", "Spruce Planks")
            assertThat(search("ick").getOrNull()?.map { it.name }).containsExactly("Stick", "Wooden Pickaxe").inOrder()
        }

    @Test
    fun searchListsOnlyCraftableItems() =
        runTest {
            assertThat(search("glass").getOrNull()).isEmpty()
            assertThat(search(" ").getOrNull()?.map { it.id }).contains("beacon")
            assertThat(search("").getOrNull()?.map { it.id }).doesNotContain("glass")
        }
}
