package dev.cniekirk.wikidroid.core.domain.recipes

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.testing.recipes.TestRecipes
import org.junit.Test

class RecipeGraphTest {
    private val graph = RecipeGraph(TestRecipes.miniVanilla)

    @Test
    fun theRecipeNamedAfterAnItemWinsATie() {
        // Four sticks from two planks (half a log) is cheaper than one from two bamboo anyway; the tie-break is the id.
        assertThat(graph.defaultRecipe("stick")?.id).isEqualTo("stick")
    }

    @Test
    fun unpackingIsNeverUsable() {
        assertThat(graph.usableRecipes("iron_ingot").map { it.id }).containsExactly("iron_ingot_from_nuggets")
        assertThat(graph.usableRecipes("iron_nugget")).isEmpty()
        assertThat(graph.defaultRecipe("iron_nugget")).isNull()
    }

    @Test
    fun theCheapestRecipeIsTheDefault() {
        assertThat(graph.defaultRecipe("white_wool")?.id).isEqualTo("white_wool_from_string")
    }

    @Test
    fun theFirstOptionWinsATie() {
        val stick = checkNotNull(graph.recipe("stick"))

        assertThat(graph.defaultOption(stick, stick.ingredients.single())).isEqualTo("oak_planks")
    }

    @Test
    fun aRecipeNeverTakesItsOwnResult() {
        val dye = checkNotNull(graph.recipe("dye_blue_wool"))

        assertThat(graph.options(dye, dye.ingredients[1])).containsExactly("white_wool")
    }
}
