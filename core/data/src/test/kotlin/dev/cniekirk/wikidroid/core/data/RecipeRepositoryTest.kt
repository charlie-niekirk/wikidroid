package dev.cniekirk.wikidroid.core.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.getOrNull
import dev.cniekirk.wikidroid.core.model.recipe.RecipeBook
import dev.cniekirk.wikidroid.core.testing.RobolectricTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

/** Reads the real bundled asset, so a regenerated book that breaks the format or its invariants fails here. */
class RecipeRepositoryTest : RobolectricTest() {
    private val baseUrl = "https://minecraft.wiki/".toHttpUrl()
    private val repository =
        AssetRecipeRepository(ApplicationProvider.getApplicationContext<Application>(), baseUrl, Dispatchers.Unconfined)

    private val book: RecipeBook by lazy { checkNotNull(runBlocking { repository.recipeBook() }.getOrNull()) }

    @Test
    fun theBookHasAVersionAndTheBeaconRecipe() {
        assertThat(book.minecraftVersion).isNotEmpty()
        val beacon = book.recipes.single { it.id == "beacon" }
        assertThat(beacon.ingredients.associate { it.options.single() to it.count })
            .containsExactly("glass", 5, "nether_star", 1, "obsidian", 3)
        assertThat(beacon.small).isFalse()
    }

    @Test
    fun tagsKeepTheGamesOrder() {
        val stick = book.recipes.single { it.id == "stick" }

        assertThat(stick.ingredients.single().tag).isEqualTo("planks")
        assertThat(
            stick.ingredients
                .single()
                .options
                .first(),
        ).isEqualTo("oak_planks")
    }

    @Test
    fun furnaceOutputsAreRawAndUnpackingIsFlagged() {
        assertThat(book.rawByDefault).containsAtLeast("iron_ingot", "glass", "diamond")
        assertThat(book.recipes.single { it.id == "iron_ingot_from_iron_block" }.unpacks).isTrue()
        assertThat(book.recipes.single { it.id == "iron_block" }.unpacks).isFalse()
    }

    @Test
    fun everyItemARecipeNamesIsKnownAndNamesAreUnique() {
        val ids = book.recipes.flatMap { recipe -> listOf(recipe.result) + recipe.ingredients.flatMap { it.options } }

        assertThat(book.items.keys).containsAtLeastElementsIn(ids.toSet())
        assertThat(book.items.values.map { it.name }).containsNoDuplicates()
    }

    @Test
    fun iconsLoadFromTheWikisImages() {
        assertThat(book.item("iron_ingot").iconUrl).isEqualTo("https://minecraft.wiki/images/Invicon_Iron_Ingot.png")
    }

    @Test
    fun theBookIsReadOnce() {
        runBlocking {
            assertThat(repository.recipeBook().getOrNull()).isSameInstanceAs(repository.recipeBook().getOrNull())
        }
    }

    @Test
    fun anUnknownFormatOrBrokenJsonIsAParseError() {
        assertThat(
            parseRecipeBook("""{"format":2,"minecraftVersion":"1","items":[],"tags":[],"recipes":[]}""", baseUrl),
        ).isEqualTo(Result.Failure(DataError.Parse))
        assertThat(parseRecipeBook("not json", baseUrl)).isEqualTo(Result.Failure(DataError.Parse))
    }
}
