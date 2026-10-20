package dev.cniekirk.wikidroid.feature.explore

import androidx.lifecycle.ViewModel
import dev.cniekirk.wikidroid.core.common.onFailure
import dev.cniekirk.wikidroid.core.common.onSuccess
import dev.cniekirk.wikidroid.core.data.WikiInfoRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ExploreViewModel(
    private val wikiInfo: WikiInfoRepository,
) : ViewModel(),
    OrbitContainerHost<ExploreState, ExploreState, ExploreEffect> {
    override val container =
        orbitContainer<ExploreState, ExploreEffect>(ExploreState()) { loadVersions() }

    fun onAction(action: ExploreAction) {
        when (action) {
            ExploreAction.RetryVersions -> loadVersions()

            ExploreAction.OpenRandomArticle -> openRandomArticle()

            // Navigation is the route's job.
            is ExploreAction.OpenArticle, is ExploreAction.OpenCategory, ExploreAction.OpenLibrary -> Unit
        }
    }

    /** Loads the versions when the screen opens, and again on retry. */
    private fun loadVersions() =
        intent {
            reduce { state.copy(versions = VersionsState.Loading) }
            val result = wikiInfo.latestVersions()
            reduce { state.withVersions(result) }
        }

    private fun openRandomArticle() =
        intent {
            if (state.isLoadingRandom) return@intent
            reduce { state.copy(isLoadingRandom = true) }
            wikiInfo
                .randomArticle()
                .onSuccess { postSideEffect(ExploreEffect.OpenArticle(it.title)) }
                .onFailure { postSideEffect(ExploreEffect.RandomArticleFailed) }
            reduce { state.copy(isLoadingRandom = false) }
        }
}
