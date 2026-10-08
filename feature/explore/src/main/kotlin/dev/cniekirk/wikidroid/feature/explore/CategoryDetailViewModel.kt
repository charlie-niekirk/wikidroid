package dev.cniekirk.wikidroid.feature.explore

import androidx.lifecycle.ViewModel
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.data.CategoryRepository
import dev.cniekirk.wikidroid.core.model.Category
import dev.cniekirk.wikidroid.core.model.CategoryMember
import dev.cniekirk.wikidroid.core.model.Paged
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.syntax.Syntax
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
            CategoryDetailAction.Retry -> retry()

            CategoryDetailAction.LoadMore -> loadMore()

            // Navigation is the route's job.
            is CategoryDetailAction.OpenArticle,
            is CategoryDetailAction.OpenCategory,
            CategoryDetailAction.Back,
            -> Unit
        }
    }

    private fun retry() =
        intent {
            if (state.phase is CategoryPhase.Failed) loadFirstBatch()
        }

    private fun loadMore() =
        intent {
            if (state.phase == CategoryPhase.Loaded) loadNextBatch()
        }

    private suspend fun Syntax<CategoryDetailState, Nothing>.loadFirstBatch() {
        if (!batchLock.tryLock()) return
        try {
            reduce { state.copy(phase = CategoryPhase.Loading) }
            when (val result = categories.getMembers(category, continuation = null)) {
                is Result.Success -> reduce { state.withBatch(result.data).copy(phase = CategoryPhase.Loaded) }
                is Result.Failure -> reduce { state.copy(phase = CategoryPhase.Failed(result.error)) }
            }
        } finally {
            batchLock.unlock()
        }
    }

    private suspend fun Syntax<CategoryDetailState, Nothing>.loadNextBatch() {
        if (!batchLock.tryLock()) return
        try {
            // Read the token under the lock: a request that waited behind another one must not repeat its batch.
            val token = state.continuation ?: return
            reduce { state.copy(isLoadingMore = true, loadMoreFailed = false) }
            when (val result = categories.getMembers(category, token)) {
                is Result.Success -> reduce { state.withBatch(result.data).copy(isLoadingMore = false) }
                is Result.Failure -> reduce { state.copy(isLoadingMore = false, loadMoreFailed = true) }
            }
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

/** Appends [batch] and takes over its continuation token. Repeats of an earlier entry are dropped. */
private fun CategoryDetailState.withBatch(batch: Paged<CategoryMember>): CategoryDetailState {
    val newSubcategories = batch.items.filterIsInstance<CategoryMember.Subcategory>().map { it.category }
    val newPages = batch.items.filterIsInstance<CategoryMember.Page>().map { it.summary }
    return copy(
        subcategories = (subcategories + newSubcategories).distinctBy { it.name }.toImmutableList(),
        pages = (pages + newPages).distinctBy { it.title }.toImmutableList(),
        continuation = batch.continuation,
    )
}
