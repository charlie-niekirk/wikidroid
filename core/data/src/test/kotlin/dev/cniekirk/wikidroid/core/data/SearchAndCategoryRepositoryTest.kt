package dev.cniekirk.wikidroid.core.data

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.getOrNull
import dev.cniekirk.wikidroid.core.model.Category
import dev.cniekirk.wikidroid.core.model.CategoryMember
import dev.cniekirk.wikidroid.core.network.PageList
import dev.cniekirk.wikidroid.core.network.dto.PageQueryResponse
import dev.cniekirk.wikidroid.core.network.dto.RestPageDto
import dev.cniekirk.wikidroid.core.network.dto.RestSearchResponse
import dev.cniekirk.wikidroid.core.testing.Fixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

private val json = Json { ignoreUnknownKeys = true }

private fun pagesFrom(fixture: String) =
    json.decodeFromString<PageQueryResponse>(Fixtures.read("network/$fixture")).query!!.pages

class SearchRepositoryTest {
    private val remote = FakeRemote()
    private val preferences = FakePreferencesDataSource()
    private val repository = WikiSearchRepository(remote, preferences)

    @Test
    fun `autocomplete maps the real rest response`() =
        runTest {
            remote.autocompleteResult =
                Result.Success(
                    json.decodeFromString<RestSearchResponse>(Fixtures.read("network/rest_title_search.json")).pages,
                )

            val results = repository.autocomplete("diamond").getOrNull()!!

            assertThat(results.map { it.title }).containsExactly("Diamond", "Diamond Ore", "Diamond Chicken").inOrder()
            assertThat(results.first().thumbnailUrl).contains("Diamond_JE3_BE3.png")
            // The excerpt just repeats the title, so there is no description to show.
            assertThat(results.first().description).isNull()
        }

    @Test
    fun `autocomplete falls back to the excerpt with its highlight markup removed`() =
        runTest {
            remote.autocompleteResult =
                Result.Success(
                    listOf(
                        RestPageDto(
                            id = 1,
                            key = "Diamond_Sword",
                            title = "Diamond Sword",
                            excerpt = "A <span class=\"searchmatch\">diamond</span> sword",
                        ),
                    ),
                )

            val summary = repository.autocomplete("diamond").getOrNull()!!.single()

            assertThat(summary.description).isEqualTo("A diamond sword")
        }

    @Test
    fun `blank queries make no request`() =
        runTest {
            assertThat(repository.autocomplete("  ").getOrNull()).isEmpty()
            assertThat(repository.search("").getOrNull()!!.items).isEmpty()
            assertThat(repository.search("").getOrNull()!!.hasMore).isFalse()
            assertThat(remote.calls).isEmpty()
        }

    @Test
    fun `search maps pages and passes the continuation through`() =
        runTest {
            remote.searchResult = Result.Success(PageList(pagesFrom("search_creeper.json"), continuation = "next"))

            val page = repository.search(" creeper ", continuation = "token").getOrNull()!!

            assertThat(remote.calls).containsExactly("search:creeper:token")
            assertThat(page.items.map { it.title }).containsExactly("Creeper", "Creeper Head", "Banner").inOrder()
            assertThat(page.items.first().pageUrl).isEqualTo("https://minecraft.wiki/w/Creeper")
            assertThat(page.items.first().description).isNotEmpty()
            assertThat(page.continuation).isEqualTo("next")
            assertThat(page.hasMore).isTrue()
        }

    @Test
    fun `failures are passed through`() =
        runTest {
            remote.searchResult = Result.Failure(DataError.Network())

            assertThat(repository.search("creeper")).isEqualTo(Result.Failure(DataError.Network()))
        }

    @Test
    fun `recent searches go through the preferences store`() =
        runTest {
            repository.saveRecentSearch("creeper")
            repository.saveRecentSearch("diamond")
            repository.removeRecentSearch("creeper")
            assertThat(repository.recentSearches.first()).containsExactly("diamond")

            repository.clearRecentSearches()
            assertThat(repository.recentSearches.first()).isEmpty()
        }
}

class CategoryRepositoryTest {
    private val remote = FakeRemote()
    private val repository = WikiCategoryRepository(remote)

    @Test
    fun `members put subcategories first and sort each group by title`() =
        runTest {
            remote.categoryResult = Result.Success(PageList(pagesFrom("category_mobs.json"), continuation = "more"))

            val batch = repository.getMembers(Category("Mobs"), continuation = "token").getOrNull()!!

            assertThat(remote.calls).containsExactly("category:Category:Mobs:token")
            val labels =
                batch.items.map {
                    when (it) {
                        is CategoryMember.Subcategory -> "category ${it.category.name}"
                        is CategoryMember.Page -> "page ${it.summary.title}"
                    }
                }
            assertThat(labels)
                .containsExactly(
                    "category Boss mobs",
                    "category End mobs",
                    "page Animal",
                    "page Armor Stand",
                    "page Mob",
                    "page Monster",
                ).inOrder()
            assertThat(batch.continuation).isEqualTo("more")
        }

    @Test
    fun `an empty category is an empty batch`() =
        runTest {
            remote.categoryResult = Result.Success(PageList(emptyList(), null))

            val batch = repository.getMembers(Category("Nothing")).getOrNull()!!

            assertThat(batch.items).isEmpty()
            assertThat(batch.hasMore).isFalse()
        }

    @Test
    fun `failures are passed through`() =
        runTest {
            remote.categoryResult = Result.Failure(DataError.Api("badtitle", null))

            assertThat(
                repository.getMembers(Category("Mobs")),
            ).isEqualTo(Result.Failure(DataError.Api("badtitle", null)))
        }
}
