package dev.cniekirk.wikidroid.core.data

import android.app.Application
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.IoDispatcher
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.dataResultOf
import dev.cniekirk.wikidroid.core.common.flatMap
import dev.cniekirk.wikidroid.core.common.onSuccess
import dev.cniekirk.wikidroid.core.model.recipe.Ingredient
import dev.cniekirk.wikidroid.core.model.recipe.Recipe
import dev.cniekirk.wikidroid.core.model.recipe.RecipeBook
import dev.cniekirk.wikidroid.core.model.recipe.RecipeItem
import dev.cniekirk.wikidroid.core.network.WikiBaseUrl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl

interface RecipeRepository {
    /** The bundled Java Edition crafting recipes. Loaded once; later calls return the same book. */
    suspend fun recipeBook(): Result<RecipeBook, DataError>
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class AssetRecipeRepository(
    private val application: Application,
    @WikiBaseUrl private val baseUrl: HttpUrl,
    @IoDispatcher private val io: CoroutineDispatcher,
) : RecipeRepository {
    private val mutex = Mutex()
    private var book: RecipeBook? = null

    override suspend fun recipeBook(): Result<RecipeBook, DataError> =
        mutex.withLock {
            book?.let { return@withLock Result.Success(it) }
            withContext(io) {
                dataResultOf {
                    application.assets
                        .open(ASSET)
                        .bufferedReader()
                        .use { it.readText() }
                }.flatMap { parseRecipeBook(it, baseUrl) }
            }.onSuccess { book = it }
        }

    private companion object {
        const val ASSET = "recipes/java.json"
    }
}

/** [decodeRecipeBook], with anything wrong in [text] reported as [DataError.Parse]. */
internal fun parseRecipeBook(
    text: String,
    baseUrl: HttpUrl,
): Result<RecipeBook, DataError> = dataResultOf(mapError = { DataError.Parse }) { decodeRecipeBook(text, baseUrl) }

private val recipeJson = Json { ignoreUnknownKeys = true }

/** Reads the generated asset (see `scripts/recipes`). Icons resolve against [baseUrl]'s `images/` folder. */
internal fun decodeRecipeBook(
    text: String,
    baseUrl: HttpUrl,
): RecipeBook {
    val dto = recipeJson.decodeFromString(RecipeBookDto.serializer(), text)
    require(dto.format == SUPPORTED_FORMAT) { "Unsupported recipe format ${dto.format}" }
    val tags = dto.tags.associate { it.id to it.items.toImmutableList() }
    return RecipeBook(
        minecraftVersion = dto.minecraftVersion,
        items =
            dto.items
                .associate { item ->
                    item.id to
                        RecipeItem(
                            id = item.id,
                            name = item.name,
                            iconUrl = item.icon?.let { baseUrl.resolve("images/$it")?.toString() },
                        )
                }.toImmutableMap(),
        recipes = dto.recipes.map { it.toRecipe(tags) }.toImmutableList(),
        rawByDefault =
            dto.items
                .filter { it.raw }
                .map { it.id }
                .toImmutableSet(),
    )
}

private const val SUPPORTED_FORMAT = 1

private fun RecipeDto.toRecipe(tags: Map<String, List<String>>): Recipe =
    Recipe(
        id = id,
        result = result,
        count = count,
        ingredients =
            ingredients
                .map { ingredient ->
                    val options = ingredient.tag?.let { requireNotNull(tags[it]) { "Unknown tag $it in $id" } }
                    Ingredient(
                        count = ingredient.count,
                        options = (options ?: ingredient.items.orEmpty()).toImmutableList(),
                        tag = ingredient.tag,
                    )
                }.toImmutableList(),
        unpacks = unpack,
        small = small,
    )

@Serializable
private data class RecipeBookDto(
    val format: Int,
    val minecraftVersion: String,
    val items: List<ItemDto>,
    val tags: List<TagDto>,
    val recipes: List<RecipeDto>,
)

@Serializable
private data class ItemDto(
    val id: String,
    val name: String,
    val icon: String? = null,
    val raw: Boolean = false,
)

@Serializable
private data class TagDto(
    val id: String,
    val items: List<String>,
)

@Serializable
private data class RecipeDto(
    val id: String,
    val result: String,
    val count: Int = 1,
    val small: Boolean = true,
    val unpack: Boolean = false,
    val ingredients: List<IngredientDto>,
)

@Serializable
private data class IngredientDto(
    val count: Int,
    val items: List<String>? = null,
    val tag: String? = null,
)
