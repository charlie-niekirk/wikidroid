package dev.cniekirk.wikidroid.feature.explore

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Category
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
