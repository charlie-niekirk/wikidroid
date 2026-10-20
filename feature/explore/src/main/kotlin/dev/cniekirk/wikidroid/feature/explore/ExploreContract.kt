package dev.cniekirk.wikidroid.feature.explore

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.fold
import dev.cniekirk.wikidroid.core.model.Category
import dev.cniekirk.wikidroid.core.model.LatestVersions
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ExploreState(
    val versions: VersionsState = VersionsState.Loading,
    val isLoadingRandom: Boolean = false,
    val categories: ImmutableList<Category> = ExploreCategories,
) {
    fun withVersions(result: Result<LatestVersions, DataError>): ExploreState =
        copy(versions = result.fold(onSuccess = VersionsState::Loaded, onFailure = VersionsState::Failed))
}

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
 * What the user can do on the Explore screen. [OpenArticle], [OpenCategory] and [OpenLibrary] are navigation: the
 * route handles them and the ViewModel never sees them.
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

    /** The top bar's Library button: bookmarks and history. */
    data object OpenLibrary : ExploreAction
}

sealed interface ExploreEffect {
    data class OpenArticle(
        val title: String,
    ) : ExploreEffect

    data object RandomArticleFailed : ExploreEffect
}
