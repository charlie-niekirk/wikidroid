package dev.cniekirk.wikidroid.feature.tools.calculator

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.domain.recipes.CalculateCraftingPlanUseCase
import dev.cniekirk.wikidroid.core.domain.recipes.SearchRecipeItemsUseCase
import dev.cniekirk.wikidroid.core.model.recipe.RecipeItem
import dev.cniekirk.wikidroid.core.testing.MainDispatcherRule
import dev.cniekirk.wikidroid.core.testing.fake.FakeRecipeRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.orbitmvi.orbit.test.OrbitTestContext
import org.orbitmvi.orbit.test.test

class ResourceCalculatorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val recipes = FakeRecipeRepository()
    private val beacon = RecipeItem("beacon", "Beacon")
    private val torch = RecipeItem("torch", "Torch")

    private fun viewModel() =
        ResourceCalculatorViewModel(
            recipes,
            CalculateCraftingPlanUseCase(recipes, UnconfinedTestDispatcher()),
            SearchRecipeItemsUseCase(recipes),
        )

    private suspend fun TestScope.inCalculator(
        viewModel: ResourceCalculatorViewModel = viewModel(),
        body: suspend OrbitTestContext<CalculatorState, Nothing, ResourceCalculatorViewModel>.(
            act: (CalculatorAction) -> Unit,
            state: () -> CalculatorState,
        ) -> Unit,
    ) {
        viewModel.test(this) {
            runOnCreate()
            runCurrent()
            body(
                { action ->
                    viewModel.onAction(action)
                    runCurrent()
                },
                { containerHost.container.stateFlow.value },
            )
            cancelAndIgnoreRemainingItems()
        }
    }

    private val CalculatorState.rawCounts get() = plan.raw.associate { it.item.id to it.count }

    @Test
    fun opensWithTheBooksVersion() =
        runTest {
            inCalculator { _, state ->
                assertThat(state().minecraftVersion).isEqualTo("26.3")
                assertThat(state().loadError).isNull()
            }
        }

    @Test
    fun aBookThatFailsToLoadCanBeRetried() =
        runTest {
            recipes.bookResult = Result.Failure(DataError.Parse)
            val viewModel = viewModel()
            inCalculator(viewModel) { act, state ->
                assertThat(state().loadError).isEqualTo(DataError.Parse)

                recipes.bookResult = FakeRecipeRepository().bookResult
                act(CalculatorAction.Retry)

                assertThat(state().loadError).isNull()
                assertThat(state().minecraftVersion).isEqualTo("26.3")
            }
        }

    @Test
    fun addingAndChangingTargetsPlansAgain() =
        runTest {
            inCalculator { act, state ->
                act(CalculatorAction.Target.Add(beacon))
                assertThat(state().rawCounts).containsExactly("glass", 5L, "obsidian", 3L, "nether_star", 1L)

                act(CalculatorAction.Target.SetQuantity("beacon", 2))
                assertThat(state().rawCounts).containsExactly("glass", 10L, "obsidian", 6L, "nether_star", 2L)

                act(CalculatorAction.Target.Add(torch))
                assertThat(state().rawCounts).containsAtLeast("coal", 1L, "oak_log", 1L)

                act(CalculatorAction.Target.Remove("beacon"))
                act(CalculatorAction.Target.Remove("torch"))
                assertThat(state().plan.raw).isEmpty()
            }
        }

    @Test
    fun choicesPlanAgainAndResetUndoesThem() =
        runTest {
            inCalculator { act, state ->
                act(CalculatorAction.Target.Add(torch))
                act(CalculatorAction.Choice.ChooseRecipe("stick", "stick_from_bamboo_item"))
                assertThat(state().rawCounts).containsExactly("coal", 1L, "bamboo", 2L)

                act(CalculatorAction.Choice.Own("stick"))
                assertThat(state().rawCounts).containsExactly("coal", 1L, "stick", 1L)

                act(CalculatorAction.Choice.Reset)
                assertThat(state().rawCounts).containsExactly("coal", 1L, "oak_log", 1L)
            }
        }

    @Test
    fun aVariantChoiceAppliesToTheTag() =
        runTest {
            inCalculator { act, state ->
                act(CalculatorAction.Target.Add(torch))
                act(CalculatorAction.Choice.ChooseVariant("#planks", "spruce_planks"))

                assertThat(state().rawCounts).containsExactly("coal", 1L, "spruce_log", 1L)
            }
        }

    @Test
    fun searchFindsCraftableItems() =
        runTest {
            inCalculator { act, state ->
                act(CalculatorAction.Sheet.OpenPicker)
                act(CalculatorAction.QueryChanged("bea"))

                assertThat(state().sheet).isEqualTo(CalculatorSheet.Picker)
                assertThat(state().searchResults?.map { it.id }).containsExactly("beacon")

                act(CalculatorAction.Target.Add(beacon))
                assertThat(state().sheet).isNull()
            }
        }

    @Test
    fun navigationActionsLeaveTheStateAlone() =
        runTest {
            inCalculator { act, state ->
                val before = state()
                act(CalculatorAction.Back)
                act(CalculatorAction.OpenArticle("Beacon"))

                assertThat(state()).isEqualTo(before)
            }
        }
}
