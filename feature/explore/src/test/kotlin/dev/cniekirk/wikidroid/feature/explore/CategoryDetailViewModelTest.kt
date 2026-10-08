package dev.cniekirk.wikidroid.feature.explore

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Category
import dev.cniekirk.wikidroid.core.model.CategoryMember
import dev.cniekirk.wikidroid.core.model.Paged
import dev.cniekirk.wikidroid.core.testing.MainDispatcherRule
import dev.cniekirk.wikidroid.core.testing.fake.FakeCategoryRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.orbitmvi.orbit.test.test

class CategoryDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeCategoryRepository()

    private fun page(title: String) = CategoryMember.Page(ArticleSummary(title = title))

    private fun subcategory(name: String) = CategoryMember.Subcategory(Category(name))

    private fun batch(
        continuation: String?,
        vararg members: CategoryMember,
    ): Result<Paged<CategoryMember>, DataError> =
        Result.Success(Paged(members.toList().toImmutableList(), continuation))

    private fun initial(title: String = "Hostile mobs") = CategoryDetailState(title = title)

    @Test
    fun loadsTheFirstBatchSplitIntoSubcategoriesAndPages() =
        runTest {
            repository.handler = { _, _ -> batch("next", subcategory("Undead mobs"), page("Creeper"), page("Zombie")) }
            val viewModel = CategoryDetailViewModel("Hostile mobs", repository)

            viewModel.test(this) {
                runOnCreate()
                expectInitialState()
                expectState {
                    copy(
                        phase = CategoryPhase.Loaded,
                        subcategories = persistentListOf(Category("Undead mobs")),
                        pages = persistentListOf(ArticleSummary(title = "Creeper"), ArticleSummary(title = "Zombie")),
                        continuation = "next",
                    )
                }
            }

            assertThat(repository.requests).containsExactly(Category("Hostile mobs") to null)
        }

    @Test
    fun titlesAreShownWithSpacesWhateverTheKeySpelling() {
        val viewModel = CategoryDetailViewModel("Hostile_mobs", repository)

        assertThat(viewModel.container.stateFlow.value.title).isEqualTo("Hostile mobs")
    }

    @Test
    fun anEmptyCategoryIsLoadedAndEmpty() =
        runTest {
            val viewModel = CategoryDetailViewModel("Empty", repository)

            viewModel.test(this) {
                runOnCreate()
                expectInitialState()
                expectState { copy(phase = CategoryPhase.Loaded) }

                assertThat(containerHost.container.stateFlow.value.isEmpty).isTrue()
            }
        }

    @Test
    fun aFailedFirstBatchCanBeRetried() =
        runTest {
            repository.handler = { _, _ -> Result.Failure(DataError.Network()) }
            val viewModel = CategoryDetailViewModel("Hostile mobs", repository)

            viewModel.test(this) {
                runOnCreate()
                expectInitialState()
                expectState { copy(phase = CategoryPhase.Failed(DataError.Network())) }

                repository.handler = { _, _ -> batch(null, page("Creeper")) }
                containerHost.onAction(CategoryDetailAction.Retry)

                expectState { copy(phase = CategoryPhase.Loading) }
                expectState {
                    copy(phase = CategoryPhase.Loaded, pages = persistentListOf(ArticleSummary(title = "Creeper")))
                }
            }
        }

    @Test
    fun retryDoesNothingUnlessTheFirstBatchFailed() =
        runTest {
            repository.handler = { _, _ -> batch(null, page("Creeper")) }
            val viewModel = CategoryDetailViewModel("Hostile mobs", repository)

            viewModel.test(this) {
                runOnCreate()
                skipItems(1)

                containerHost.onAction(CategoryDetailAction.Retry)

                expectNoItems()
                assertThat(repository.requests).hasSize(1)
            }
        }

    @Test
    fun loadMoreAppendsTheNextBatchWithItsToken() =
        runTest {
            repository.handler = { _, token ->
                when (token) {
                    null -> batch("two", subcategory("Undead mobs"), page("Creeper"))
                    else -> batch(null, subcategory("Nether mobs"), page("Blaze"), page("Creeper"))
                }
            }
            val viewModel = CategoryDetailViewModel("Hostile mobs", repository)

            viewModel.test(this) {
                runOnCreate()
                expectState {
                    copy(
                        phase = CategoryPhase.Loaded,
                        subcategories = persistentListOf(Category("Undead mobs")),
                        pages = persistentListOf(ArticleSummary(title = "Creeper")),
                        continuation = "two",
                    )
                }

                containerHost.onAction(CategoryDetailAction.LoadMore)

                expectState { copy(isLoadingMore = true) }
                expectState {
                    copy(
                        subcategories = persistentListOf(Category("Undead mobs"), Category("Nether mobs")),
                        // "Creeper" came back again and is not repeated.
                        pages = persistentListOf(ArticleSummary(title = "Creeper"), ArticleSummary(title = "Blaze")),
                        continuation = null,
                        isLoadingMore = false,
                    )
                }
            }

            assertThat(repository.requests.map { it.second }).containsExactly(null, "two").inOrder()
        }

    @Test
    fun loadMoreStopsWhenEverythingIsLoaded() =
        runTest {
            repository.handler = { _, _ -> batch(null, page("Creeper")) }
            val viewModel = CategoryDetailViewModel("Hostile mobs", repository)

            viewModel.test(this) {
                runOnCreate()
                skipItems(1)

                containerHost.onAction(CategoryDetailAction.LoadMore)

                expectNoItems()
                assertThat(repository.requests).hasSize(1)
            }
        }

    @Test
    fun loadMoreWaitsForTheFirstBatch() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.handler = { _, _ ->
                gate.await()
                batch("two", page("Creeper"))
            }
            val viewModel = CategoryDetailViewModel("Hostile mobs", repository)

            viewModel.test(this) {
                runOnCreate()
                expectInitialState()

                containerHost.onAction(CategoryDetailAction.LoadMore)
                this@runTest.runCurrent()
                gate.complete(Unit)

                expectState {
                    copy(
                        phase = CategoryPhase.Loaded,
                        pages = persistentListOf(ArticleSummary(title = "Creeper")),
                        continuation = "two",
                    )
                }
                expectNoItems()
                assertThat(repository.requests).hasSize(1)
            }
        }

    @Test
    fun repeatedLoadMoreRequestsFetchTheBatchOnce() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.handler = { _, token ->
                if (token == null) {
                    batch("two", page("Creeper"))
                } else {
                    gate.await()
                    batch(null, page("Blaze"))
                }
            }
            val viewModel = CategoryDetailViewModel("Hostile mobs", repository)

            viewModel.test(this) {
                runOnCreate()
                expectState {
                    copy(
                        phase = CategoryPhase.Loaded,
                        pages = persistentListOf(ArticleSummary(title = "Creeper")),
                        continuation = "two",
                    )
                }

                containerHost.onAction(CategoryDetailAction.LoadMore)
                expectState { copy(isLoadingMore = true) }
                containerHost.onAction(CategoryDetailAction.LoadMore)
                containerHost.onAction(CategoryDetailAction.LoadMore)
                gate.complete(Unit)

                expectState {
                    copy(
                        pages = persistentListOf(ArticleSummary(title = "Creeper"), ArticleSummary(title = "Blaze")),
                        continuation = null,
                        isLoadingMore = false,
                    )
                }
                expectNoItems()
            }

            assertThat(repository.requests.map { it.second }).containsExactly(null, "two").inOrder()
        }

    @Test
    fun aFailedLaterBatchKeepsWhatIsShownAndCanBeRetried() =
        runTest {
            repository.handler = { _, token ->
                if (token == null) batch("two", page("Creeper")) else Result.Failure(DataError.Network())
            }
            val viewModel = CategoryDetailViewModel("Hostile mobs", repository)

            viewModel.test(this) {
                runOnCreate()
                expectState {
                    copy(
                        phase = CategoryPhase.Loaded,
                        pages = persistentListOf(ArticleSummary(title = "Creeper")),
                        continuation = "two",
                    )
                }

                containerHost.onAction(CategoryDetailAction.LoadMore)

                expectState { copy(isLoadingMore = true) }
                expectState { copy(isLoadingMore = false, loadMoreFailed = true) }

                repository.handler = { _, _ -> batch(null, page("Blaze")) }
                containerHost.onAction(CategoryDetailAction.LoadMore)

                expectState { copy(isLoadingMore = true, loadMoreFailed = false) }
                expectState {
                    copy(
                        pages = persistentListOf(ArticleSummary(title = "Creeper"), ArticleSummary(title = "Blaze")),
                        continuation = null,
                        isLoadingMore = false,
                    )
                }
            }
        }

    @Test
    fun navigationActionsAreLeftToTheRoute() =
        runTest {
            val viewModel = CategoryDetailViewModel("Hostile mobs", repository)

            viewModel.test(this) {
                expectInitialState()

                containerHost.onAction(CategoryDetailAction.OpenArticle("Creeper"))
                containerHost.onAction(CategoryDetailAction.OpenCategory("Undead mobs"))
                containerHost.onAction(CategoryDetailAction.Back)

                expectNoItems()
                assertThat(repository.requests).isEmpty()
            }
        }
}
