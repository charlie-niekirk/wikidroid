package dev.cniekirk.wikidroid.core.domain.recipes

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.recipe.CraftingPlan
import dev.cniekirk.wikidroid.core.model.recipe.CraftingTarget
import dev.cniekirk.wikidroid.core.model.recipe.PlanChoices
import dev.cniekirk.wikidroid.core.testing.recipes.TestRecipes
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test

class CraftingPlannerTest {
    private val planner = CraftingPlanner(RecipeGraph(TestRecipes.miniVanilla))

    private fun plan(
        vararg targets: Pair<String, Int>,
        choices: PlanChoices = PlanChoices(),
    ): CraftingPlan = planner.plan(targets.map { (id, quantity) -> CraftingTarget(id, quantity) }, choices)

    private val CraftingPlan.rawCounts get() = raw.associate { it.item.id to it.count }
    private val CraftingPlan.stepCounts get() = steps.associate { it.output.item.id to it.crafts }
    private val CraftingPlan.leftoverCounts get() = leftovers.associate { it.item.id to it.count }

    @Test
    fun aBeaconTakesGlassObsidianAndANetherStar() {
        val plan = plan("beacon" to 1)

        assertThat(plan.rawCounts).containsExactly("glass", 5L, "obsidian", 3L, "nether_star", 1L)
        assertThat(plan.stepCounts).containsExactly("beacon", 1L)
        assertThat(plan.steps.single().needsCraftingTable).isTrue()
        assertThat(plan.leftovers).isEmpty()
    }

    @Test
    fun twoBeaconsTakeTwiceAsMuch() {
        assertThat(plan("beacon" to 2).rawCounts).containsExactly("glass", 10L, "obsidian", 6L, "nether_star", 2L)
    }

    @Test
    fun batchesRoundUpAndLeaveTheRestOver() {
        val plan = plan("torch" to 10)

        assertThat(plan.rawCounts).containsExactly("coal", 3L, "oak_log", 1L)
        assertThat(plan.stepCounts).containsExactly("torch", 3L, "stick", 1L, "oak_planks", 1L)
        assertThat(plan.leftoverCounts).containsExactly("torch", 2L, "stick", 1L, "oak_planks", 2L)
    }

    @Test
    fun stepsComeInAnOrderThatWorks() {
        assertThat(plan("torch" to 10).steps.map { it.output.item.id })
            .containsExactly("oak_planks", "stick", "torch")
            .inOrder()
    }

    @Test
    fun anIntermediateSharedByTwoTargetsIsCraftedInOneBatch() {
        val plan = plan("wooden_pickaxe" to 1, "wooden_shovel" to 1)

        assertThat(plan.stepCounts).containsAtLeast("stick", 1L, "oak_planks", 2L)
        assertThat(plan.rawCounts).containsExactly("oak_log", 2L)
    }

    @Test
    fun aTargetThatIsAlsoAnIngredientIsOneStep() {
        val plan = plan("torch" to 4, "stick" to 2)

        assertThat(plan.steps.filter { it.output.item.id == "stick" }).hasSize(1)
        assertThat(plan.stepCounts["stick"]).isEqualTo(1L)
        assertThat(plan.leftoverCounts["stick"]).isEqualTo(1L)
    }

    @Test
    fun theSameTargetTwiceIsAddedUp() {
        assertThat(plan("beacon" to 1, "beacon" to 2).stepCounts).containsExactly("beacon", 3L)
    }

    @Test
    fun ingotsAreRawByDefaultAndNeverComeFromUnpackingBlocks() {
        val plan = plan("iron_block" to 1)

        assertThat(plan.rawCounts).containsExactly("iron_ingot", 9L)
    }

    @Test
    fun aRawByDefaultItemCanBeCraftedInstead() {
        val plan = plan("iron_block" to 1, choices = PlanChoices(craft = persistentSetOf("iron_ingot")))

        assertThat(plan.rawCounts).containsExactly("iron_nugget", 81L)
        assertThat(plan.stepCounts).containsExactly("iron_block", 1L, "iron_ingot", 9L)
    }

    @Test
    fun aRawByDefaultTargetStaysRaw() {
        val plan = plan("iron_ingot" to 5)

        assertThat(plan.rawCounts).containsExactly("iron_ingot", 5L)
        assertThat(plan.steps).isEmpty()
    }

    @Test
    fun anOwnedItemIsNotBrokenDown() {
        val plan = plan("wooden_pickaxe" to 1, choices = PlanChoices(owned = persistentSetOf("stick")))

        assertThat(plan.rawCounts).containsExactly("stick", 2L, "oak_log", 1L)
        assertThat(plan.raw.first { it.item.id == "stick" }.owned).isTrue()
        assertThat(plan.raw.first { it.item.id == "stick" }.craftable).isTrue()
        assertThat(plan.leftoverCounts).containsExactly("oak_planks", 1L)
    }

    @Test
    fun aChosenRecipeIsUsed() {
        val choices = PlanChoices(recipes = persistentMapOf("stick" to "stick_from_bamboo_item"))

        val plan = plan("torch" to 4, choices = choices)

        assertThat(plan.rawCounts).containsExactly("coal", 1L, "bamboo", 2L)
        assertThat(
            plan.steps
                .first { it.output.item.id == "stick" }
                .options
                .single { it.chosen }
                .recipeId,
        ).isEqualTo("stick_from_bamboo_item")
    }

    @Test
    fun aChosenVariantAppliesWhereverTheTagIsUsed() {
        val choices = PlanChoices(variants = persistentMapOf("#planks" to "spruce_planks"))

        val plan = plan("wooden_pickaxe" to 1, choices = choices)

        assertThat(plan.rawCounts).containsExactly("spruce_log", 2L)
    }

    @Test
    fun aStepListsTheVariantsAnIngredientCouldBe() {
        val stick = plan("stick" to 4).steps.first { it.output.item.id == "stick" }

        val planks = stick.inputs.single()
        assertThat(planks.variantKey).isEqualTo("#planks")
        assertThat(planks.options.map { it.id }).containsExactly("oak_planks", "spruce_planks").inOrder()
        assertThat(stick.options.map { it.recipeId }).containsExactly("stick", "stick_from_bamboo_item")
    }

    @Test
    fun aChoiceThatWouldNeedTheItemItselfIsIgnored() {
        // Blue wool is dyed white wool; dyeing that white wool from blue wool would need the blue wool first.
        val choices = PlanChoices(recipes = persistentMapOf("white_wool" to "dye_white_wool"))

        val plan = plan("blue_wool" to 1, choices = choices)

        assertThat(plan.ignoredChoices).containsExactly("white_wool")
        assertThat(plan.rawCounts).containsExactly("string", 4L, "lapis_lazuli", 1L)
    }

    @Test
    fun recolouringNeverTakesTheColourItMakes() {
        val plan = plan("blue_wool" to 1)

        val wool =
            plan.steps
                .first { it.output.item.id == "blue_wool" }
                .inputs
                .first { it.item.id.endsWith("wool") }
        assertThat(wool.item.id).isEqualTo("white_wool")
    }

    @Test
    fun noTargetsIsAnEmptyPlan() {
        assertThat(plan()).isEqualTo(CraftingPlan())
    }

    @Test
    fun quantitiesAreClamped() {
        assertThat(plan("beacon" to 1_000_000).stepCounts).containsExactly("beacon", CraftingPlanner.MAX_QUANTITY)
    }
}
