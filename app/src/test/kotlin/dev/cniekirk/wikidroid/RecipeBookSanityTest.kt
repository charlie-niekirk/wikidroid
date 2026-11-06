package dev.cniekirk.wikidroid

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.getOrNull
import dev.cniekirk.wikidroid.core.domain.recipes.CalculateCraftingPlanUseCase
import dev.cniekirk.wikidroid.core.model.recipe.CraftingPlan
import dev.cniekirk.wikidroid.core.model.recipe.CraftingTarget
import dev.cniekirk.wikidroid.core.model.recipe.PlanChoices
import dev.cniekirk.wikidroid.core.testing.RobolectricTest
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

/**
 * Plans with the bundled recipe book through the real graph, so a regenerated book whose defaults loop or pick
 * something silly fails here rather than in the app.
 */
class RecipeBookSanityTest : RobolectricTest() {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val graph = createGraphFactory<AppGraph.Factory>().create(application, "http://localhost/".toHttpUrl())
    private val calculate = CalculateCraftingPlanUseCase(graph.recipeRepository, Dispatchers.Unconfined)

    private fun plan(vararg targets: Pair<String, Int>): CraftingPlan =
        checkNotNull(
            runBlocking {
                calculate(targets.map { (id, quantity) -> CraftingTarget(id, quantity) }, PlanChoices()).getOrNull()
            },
        )

    private val CraftingPlan.rawCounts get() = raw.associate { it.item.id to it.count }

    @Test
    fun aBeaconTakesGlassObsidianAndANetherStar() {
        assertThat(plan("beacon" to 1).rawCounts).containsExactly("glass", 5L, "obsidian", 3L, "nether_star", 1L)
    }

    @Test
    fun torchesComeFromCoalAndOakLogs() {
        // 16 sticks are 8 planks, which take 2 logs.
        assertThat(plan("torch" to 64).rawCounts).containsExactly("coal", 16L, "oak_log", 2L)
    }

    @Test
    fun anIronBlockStopsAtIngots() {
        assertThat(plan("iron_block" to 1).rawCounts).containsExactly("iron_ingot", 9L)
    }

    @Test
    fun everyCraftableItemPlansWithItsDefaults() {
        val book = checkNotNull(runBlocking { graph.recipeRepository.recipeBook() }.getOrNull())
        val craftable = book.recipes.map { it.result }.distinct()

        craftable.forEach { item ->
            val plan = plan(item to 1)
            assertThat(plan.ignoredChoices).isEmpty()
            assertThat(plan.raw.map { it.item.id } + plan.steps.map { it.output.item.id }).contains(item)
            // Every step is reachable: a loop would leave a step that never got its demand.
            plan.steps.forEach { step -> assertThat(step.crafts).isGreaterThan(0L) }
            // Unpacking a storage block is never a default.
            plan.steps.forEach { step -> assertThat(book.recipes.first { it.id == step.recipeId }.unpacks).isFalse() }
        }
    }
}
