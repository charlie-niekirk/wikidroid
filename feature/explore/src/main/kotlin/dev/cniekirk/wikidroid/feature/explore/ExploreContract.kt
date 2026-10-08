package dev.cniekirk.wikidroid.feature.explore

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.model.Category
import dev.cniekirk.wikidroid.core.model.LatestVersions
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ExploreState(
    val versions: VersionsState = VersionsState.Loading,
    val isLoadingRandom: Boolean = false,
    val categories: ImmutableList<Category> = ExploreCategories,
)

@Immutable
sealed interface VersionsState {
    data object Loading : VersionsState

    data class Loaded(
        val versions: LatestVersions,
    ) : VersionsState

    data class Failed(
        val error: DataError,
    ) : VersionsState
}

/**
 * What the user can do on the Explore screen. [OpenArticle] and [OpenCategory] are navigation: the route
 * handles them and the ViewModel never sees them.
 */
sealed interface ExploreAction {
    data object RetryVersions : ExploreAction

    data object OpenRandomArticle : ExploreAction

    data class OpenArticle(
        val title: String,
    ) : ExploreAction

    data class OpenCategory(
        val name: String,
    ) : ExploreAction
}

sealed interface ExploreEffect {
    data class OpenArticle(
        val title: String,
    ) : ExploreEffect

    data object RandomArticleFailed : ExploreEffect
}
