package dev.cniekirk.wikidroid.feature.tools.calculator

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.getOrNull
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.domain.recipes.CalculateCraftingPlanUseCase
import dev.cniekirk.wikidroid.core.model.recipe.CraftingTarget
import dev.cniekirk.wikidroid.core.model.recipe.PlanChoices
import dev.cniekirk.wikidroid.core.model.recipe.RecipeItem
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import dev.cniekirk.wikidroid.core.testing.fake.FakeRecipeRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ResourceCalculatorScreenTest : ComposeTest() {
    private val actions = mutableListOf<CalculatorAction>()
    private val beacon = RecipeItem("beacon", "Beacon")
    private val torch = RecipeItem("torch", "Torch")

    private fun setScreen(state: CalculatorState) {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                ResourceCalculatorScreen(state = state, onAction = { actions += it })
            }
        }
    }

    /** A state with a real plan from the test recipe book. */
    private fun planned(
        vararg targets: CalculatorTarget,
        sheet: CalculatorSheet? = null,
    ): CalculatorState {
        val use = CalculateCraftingPlanUseCase(FakeRecipeRepository(), Dispatchers.Unconfined)
        val plan =
            runBlocking { use(targets.map { CraftingTarget(it.item.id, it.quantity) }, PlanChoices()) }.getOrNull()
        return CalculatorState(
            minecraftVersion = "26.3",
            targets = persistentListOf(*targets),
            plan = checkNotNull(plan),
            sheet = sheet,
        )
    }

    @Test
    fun aFailedLoadOffersARetry() {
        setScreen(CalculatorState(loadError = DataError.Parse))

        composeRule.onNodeWithText("Try again").performClick()

        assertThat(actions).containsExactly(CalculatorAction.Retry)
    }

    @Test
    fun anEmptyListInvitesTheUserToAddSomething() {
        setScreen(CalculatorState(minecraftVersion = "26.3"))

        composeRule.onNodeWithText("Nothing to make yet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add item").performClick()

        assertThat(actions).containsExactly(CalculatorAction.Sheet.OpenPicker)
    }

    @Test
    fun showsTheRawMaterialsAndSteps() {
        setScreen(planned(CalculatorTarget(beacon, 2)))

        composeRule.onNodeWithText("Raw materials").assertIsDisplayed()
        composeRule.onNodeWithText("Glass").assertIsDisplayed()
        composeRule.onNodeWithText("×10").assertIsDisplayed()
        composeRule.onNodeWithTag(CALCULATOR_LIST_TAG).performScrollToNode(hasText("1. Craft 2×"))
        composeRule.onNodeWithText("Needs a crafting table").assertIsDisplayed()
    }

    @Test
    fun tappingAMaterialShowsItsSheet() {
        setScreen(planned(CalculatorTarget(beacon, 1)))

        composeRule.onNodeWithText("Glass").performClick()

        assertThat(actions).containsExactly(CalculatorAction.Sheet.ShowItem("glass"))
    }

    @Test
    fun theQuantityFieldAndSteppersSendTheQuantity() {
        setScreen(planned(CalculatorTarget(beacon, 1)))

        composeRule.onNodeWithContentDescription("More Beacon").performClick()
        composeRule.onNodeWithTag(quantityTag("beacon")).performTextClearance()
        composeRule.onNodeWithTag(quantityTag("beacon")).performTextInput("12")
        composeRule.onNodeWithContentDescription("Remove Beacon").performClick()

        assertThat(actions)
            .containsExactly(
                CalculatorAction.Target.SetQuantity("beacon", 2),
                // Clearing the field sends nothing: an empty field is not a quantity.
                CalculatorAction.Target.SetQuantity("beacon", 12),
                CalculatorAction.Target.Remove("beacon"),
            ).inOrder()
    }

    @Test
    fun aVariantChipOpensTheChoices() {
        setScreen(planned(CalculatorTarget(torch, 4)))

        composeRule.onNodeWithTag(CALCULATOR_LIST_TAG).performScrollToNode(hasText("2 Oak Planks"))
        composeRule.onNodeWithText("2 Oak Planks").performClick()

        val show = actions.single() as CalculatorAction.Sheet.ShowVariants
        assertThat(show.key).isEqualTo("#planks")
        assertThat(show.selected).isEqualTo("oak_planks")
        assertThat(show.options.map { it.id }).containsExactly("oak_planks", "spruce_planks").inOrder()
    }

    @Test
    fun thePickerAddsWhatIsTappedAndSendsTheQuery() {
        setScreen(
            CalculatorState(
                minecraftVersion = "26.3",
                sheet = CalculatorSheet.Picker,
                searchResults = persistentListOf(beacon, torch),
            ),
        )

        composeRule.onNodeWithTag(PICKER_FIELD_TAG).performTextInput("to")
        composeRule.onNodeWithTag(PICKER_LIST_TAG).performScrollToNode(hasTestTag(pickerItemTag("torch")))
        composeRule.onNodeWithTag(pickerItemTag("torch")).performClick()

        assertThat(actions).contains(CalculatorAction.QueryChanged("to"))
        assertThat(actions.last()).isEqualTo(CalculatorAction.Target.Add(torch))
    }

    @Test
    fun theItemSheetOfACraftedItemOffersItsRecipes() {
        setScreen(planned(CalculatorTarget(torch, 4), sheet = CalculatorSheet.Item("stick")))

        composeRule.onNodeWithText("I have this").performClick()
        composeRule.onNodeWithTag(recipeOptionTag("stick_from_bamboo_item")).performClick()
        composeRule.onNodeWithText("Open in the wiki").performClick()

        assertThat(actions)
            .containsExactly(
                CalculatorAction.Choice.Own("stick"),
                CalculatorAction.Choice.ChooseRecipe("stick", "stick_from_bamboo_item"),
                CalculatorAction.OpenArticle("Stick"),
            ).inOrder()
    }

    @Test
    fun theVariantSheetPicksAnItem() {
        val options =
            persistentListOf(RecipeItem("oak_planks", "Oak Planks"), RecipeItem("spruce_planks", "Spruce Planks"))
        setScreen(
            CalculatorState(
                minecraftVersion = "26.3",
                sheet = CalculatorSheet.Variants("#planks", options, "oak_planks"),
            ),
        )

        composeRule.onNodeWithTag(VARIANT_LIST_TAG).performScrollToNode(hasTestTag(variantTag("spruce_planks")))
        composeRule.onNodeWithTag(variantTag("spruce_planks")).performClick()

        assertThat(actions).containsExactly(CalculatorAction.Choice.ChooseVariant("#planks", "spruce_planks"))
    }

    @Test
    fun resetShowsOnlyOnceSomethingWasChosen() {
        setScreen(planned(CalculatorTarget(beacon, 1)).copy(choices = PlanChoices(owned = persistentSetOf("glass"))))

        composeRule.onNodeWithContentDescription("Reset choices").performClick()

        assertThat(actions).containsExactly(CalculatorAction.Choice.Reset)
    }
}
