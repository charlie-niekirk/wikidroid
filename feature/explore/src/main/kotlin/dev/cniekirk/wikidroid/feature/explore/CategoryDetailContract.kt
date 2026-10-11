package dev.cniekirk.wikidroid.feature.explore

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Category
import dev.cniekirk.wikidroid.core.model.CategoryMember
import dev.cniekirk.wikidroid.core.model.Paged
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * A category's contents, loaded a batch at a time. [continuation] is the token for the next batch, or `null`
 * once everything is loaded. [phase] describes the first batch only; later failures set [loadMoreFailed].
 */
@Immutable
data class CategoryDetailState(
    val title: String,
    val phase: CategoryPhase = CategoryPhase.Loading,
    val subcategories: ImmutableList<Category> = persistentListOf(),
    val pages: ImmutableList<ArticleSummary> = persistentListOf(),
    val continuation: String? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
) {
    val isEmpty: Boolean get() = subcategories.isEmpty() && pages.isEmpty() && continuation == null

    /** The first batch, or why it failed. */
    fun withFirstBatch(result: Result<Paged<CategoryMember>, DataError>): CategoryDetailState =
        when (result) {
            is Result.Success -> withBatch(result.data).copy(phase = CategoryPhase.Loaded)
            is Result.Failure -> copy(phase = CategoryPhase.Failed(result.error))
        }

    /** A later batch; a failure keeps what is shown and offers a retry. */
    fun withNextBatch(result: Result<Paged<CategoryMember>, DataError>): CategoryDetailState =
        when (result) {
            is Result.Success -> withBatch(result.data).copy(isLoadingMore = false)
            is Result.Failure -> copy(isLoadingMore = false, loadMoreFailed = true)
        }

    /** Appends [batch] and takes over its continuation token. Repeats of an earlier entry are dropped. */
    private fun withBatch(batch: Paged<CategoryMember>): CategoryDetailState {
        val newSubcategories = batch.items.filterIsInstance<CategoryMember.Subcategory>().map { it.category }
        val newPages = batch.items.filterIsInstance<CategoryMember.Page>().map { it.summary }
        return copy(
            subcategories = (subcategories + newSubcategories).distinctBy { it.name }.toImmutableList(),
            pages = (pages + newPages).distinctBy { it.title }.toImmutableList(),
            continuation = batch.continuation,
        )
    }
}

@Immutable
sealed interface CategoryPhase {
    data object Loading : CategoryPhase

    data object Loaded : CategoryPhase

    data class Failed(
        val error: DataError,
    ) : CategoryPhase
}

/** [OpenArticle], [OpenCategory] and [Back] are navigation, handled by the route. */
sealed interface CategoryDetailAction {
    /** Retries the first batch after it failed. */
    data object Retry : CategoryDetailAction

    /** Loads the next batch, or retries it after it failed. Ignored while a batch is loading or none is left. */
    data object LoadMore : CategoryDetailAction

    data class OpenArticle(
        val title: String,
    ) : CategoryDetailAction

    data class OpenCategory(
        val name: String,
    ) : CategoryDetailAction

    data object Back : CategoryDetailAction
}
