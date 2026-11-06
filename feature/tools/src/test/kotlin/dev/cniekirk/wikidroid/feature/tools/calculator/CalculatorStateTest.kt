package dev.cniekirk.wikidroid.feature.tools.calculator

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.model.recipe.CraftingPlan
import dev.cniekirk.wikidroid.core.model.recipe.ItemCount
import dev.cniekirk.wikidroid.core.model.recipe.RecipeItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test

class CalculatorStateTest {
    private val beacon = RecipeItem("beacon", "Beacon")
    private val torch = RecipeItem("torch", "Torch")
    private val withBeacon = CalculatorState(targets = persistentListOf(CalculatorTarget(beacon, 1)))

    @Test
    fun addingANewItemAppendsOneAndClosesThePicker() {
        val state = withBeacon.copy(sheet = CalculatorSheet.Picker).withTargetAction(CalculatorAction.Target.Add(torch))

        assertThat(state.targets).containsExactly(CalculatorTarget(beacon, 1), CalculatorTarget(torch, 1)).inOrder()
        assertThat(state.sheet).isNull()
    }

    @Test
    fun addingAnItemAlreadyListedAddsOneMore() {
        val state = withBeacon.withTargetAction(CalculatorAction.Target.Add(beacon))

        assertThat(state.targets).containsExactly(CalculatorTarget(beacon, 2))
    }

    @Test
    fun quantitiesAreClamped() {
        val state = withBeacon.withTargetAction(CalculatorAction.Target.SetQuantity("beacon", 0))
        assertThat(state.targets.single().quantity).isEqualTo(1)

        val big = withBeacon.withTargetAction(CalculatorAction.Target.SetQuantity("beacon", 1_000_000))
        assertThat(big.targets.single().quantity).isEqualTo(CalculatorState.MAX_QUANTITY)
    }

    @Test
    fun owningAndCraftingAreOpposites() {
        val owned = withBeacon.withChoiceAction(CalculatorAction.Choice.Own("stick"))
        assertThat(owned.choices.owned).containsExactly("stick")

        val crafted = owned.withChoiceAction(CalculatorAction.Choice.Craft("stick"))
        assertThat(crafted.choices.owned).isEmpty()
        assertThat(crafted.choices.craft).containsExactly("stick")
    }

    @Test
    fun aChoiceClosesItsSheetAndResetForgetsEveryChoice() {
        val chosen =
            withBeacon
                .copy(sheet = CalculatorSheet.Item("stick"))
                .withChoiceAction(CalculatorAction.Choice.ChooseRecipe("stick", "stick_from_bamboo_item"))
                .withChoiceAction(CalculatorAction.Choice.ChooseVariant("#planks", "spruce_planks"))
        assertThat(chosen.sheet).isNull()
        assertThat(chosen.choices.recipes).containsExactly("stick", "stick_from_bamboo_item")
        assertThat(chosen.choices.variants).containsExactly("#planks", "spruce_planks")

        assertThat(chosen.withChoiceAction(CalculatorAction.Choice.Reset).choices.isEmpty).isTrue()
    }

    @Test
    fun aPlanForAnOlderRequestIsDropped() {
        val request = withBeacon.request
        val changed = withBeacon.withTargetAction(CalculatorAction.Target.SetQuantity("beacon", 2))
        val plan = CraftingPlan(leftovers = persistentListOf(ItemCount(beacon, 1)))

        assertThat(changed.withPlan(request, Result.Success(plan)).plan).isEqualTo(CraftingPlan())
        assertThat(withBeacon.withPlan(request, Result.Success(plan)).plan).isEqualTo(plan)
        assertThat(withBeacon.withPlan(request, Result.Failure(DataError.Parse)).planError).isEqualTo(DataError.Parse)
    }

    @Test
    fun resultsForAnOlderQueryAreDropped() {
        val state = CalculatorState(searchQuery = "torch")

        assertThat(state.withSearchResults("to", Result.Success(persistentListOf(beacon))).searchResults).isNull()
        assertThat(state.withSearchResults("torch", Result.Success(persistentListOf(torch))).searchResults)
            .containsExactly(torch)
    }

    @Test
    fun theRequestCarriesTargetsAndChoices() {
        val state = withBeacon.withChoiceAction(CalculatorAction.Choice.Own("glass"))

        assertThat(
            state.request.targets
                .single()
                .itemId,
        ).isEqualTo("beacon")
        assertThat(state.request.choices.owned).isEqualTo(persistentSetOf("glass"))
    }
}
