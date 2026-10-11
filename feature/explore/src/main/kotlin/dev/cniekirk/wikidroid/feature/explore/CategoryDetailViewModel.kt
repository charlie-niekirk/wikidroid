package dev.cniekirk.wikidroid.feature.explore

import androidx.lifecycle.ViewModel
import dev.cniekirk.wikidroid.core.data.CategoryRepository
import dev.cniekirk.wikidroid.core.model.Category
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.sync.Mutex
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * Lists one category: its subcategories and pages, fetched a batch at a time as the user scrolls.
 * [title] is the category name without the `Category:` prefix.
 */
@AssistedInject
class CategoryDetailViewModel(
    @Assisted title: String,
    private val categories: CategoryRepository,
) : ViewModel(),
    OrbitContainerHost<CategoryDetailState, CategoryDetailState, Nothing> {
    private val category = Category(title)

    /** Held while a batch is in flight, so a second request for the same batch is dropped, not queued. */
    private val batchLock = Mutex()

    override val container =
        orbitContainer<CategoryDetailState, Nothing>(
            CategoryDetailState(title = category.displayName),
        ) { loadFirstBatch() }

    fun onAction(action: CategoryDetailAction) {
        when (action) {
            CategoryDetailAction.Retry -> loadFirstBatch()

            CategoryDetailAction.LoadMore -> loadNextBatch()

            // Navigation is the route's job.
            is CategoryDetailAction.OpenArticle,
            is CategoryDetailAction.OpenCategory,
            CategoryDetailAction.Back,
            -> Unit
        }
    }

    /** Loads the first batch when the screen opens, and again on retry after it failed. */
    private fun loadFirstBatch() =
        intent {
            if (state.phase == CategoryPhase.Loaded || !batchLock.tryLock()) return@intent
            try {
                reduce { state.copy(phase = CategoryPhase.Loading) }
                val result = categories.getMembers(category, continuation = null)
                reduce { state.withFirstBatch(result) }
            } finally {
                batchLock.unlock()
            }
        }

    private fun loadNextBatch() =
        intent {
            if (state.phase != CategoryPhase.Loaded || !batchLock.tryLock()) return@intent
            try {
                // Read the token under the lock: a request that waited behind another one must not repeat its batch.
                val token = state.continuation ?: return@intent
                reduce { state.copy(isLoadingMore = true, loadMoreFailed = false) }
                val result = categories.getMembers(category, token)
                reduce { state.withNextBatch(result) }
            } finally {
                batchLock.unlock()
            }
        }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class, binding = binding<ManualViewModelAssistedFactory>())
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(title: String): CategoryDetailViewModel
    }
}
