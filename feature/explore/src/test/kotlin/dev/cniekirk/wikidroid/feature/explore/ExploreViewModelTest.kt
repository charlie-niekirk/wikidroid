package dev.cniekirk.wikidroid.feature.explore

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.LatestVersions
import dev.cniekirk.wikidroid.core.testing.MainDispatcherRule
import dev.cniekirk.wikidroid.core.testing.fake.FakeWikiInfoRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.orbitmvi.orbit.test.test

class ExploreViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val versions = LatestVersions(java = "26.3", javaSnapshot = null, bedrock = "26.52", bedrockPreview = null)
    private val wikiInfo = FakeWikiInfoRepository().apply { latestVersionsResult = Result.Success(versions) }

    @Test
    fun showsTheCuratedCategoriesAndLoadsTheLatestVersions() =
        runTest {
            val viewModel = ExploreViewModel(wikiInfo)

            viewModel.test(this) {
                runOnCreate()
                expectInitialState()
                expectState { copy(versions = VersionsState.Loaded(this@ExploreViewModelTest.versions)) }

                assertThat(containerHost.container.stateFlow.value.categories).isEqualTo(ExploreCategories)
            }
        }

    @Test
    fun reportsWhyTheVersionsCouldNotLoadAndRetries() =
        runTest {
            wikiInfo.latestVersionsResult = Result.Failure(DataError.Network())
            val viewModel = ExploreViewModel(wikiInfo)

            viewModel.test(this) {
                runOnCreate()
                expectInitialState()
                expectState { copy(versions = VersionsState.Failed(DataError.Network())) }

                wikiInfo.latestVersionsResult = Result.Success(versions)
                containerHost.onAction(ExploreAction.RetryVersions)

                expectState { copy(versions = VersionsState.Loading) }
                expectState { copy(versions = VersionsState.Loaded(this@ExploreViewModelTest.versions)) }
                assertThat(wikiInfo.latestVersionsCalls).isEqualTo(2)
            }
        }

    @Test
    fun openingARandomArticleEmitsItsTitle() =
        runTest {
            wikiInfo.randomArticleResult = Result.Success(ArticleSummary(title = "Allay"))
            val viewModel = ExploreViewModel(wikiInfo)

            viewModel.test(this) {
                runOnCreate()
                expectInitialState()
                expectState { copy(versions = VersionsState.Loaded(this@ExploreViewModelTest.versions)) }

                containerHost.onAction(ExploreAction.OpenRandomArticle)

                expectState { copy(isLoadingRandom = true) }
                expectSideEffect(ExploreEffect.OpenArticle("Allay"))
                expectState { copy(isLoadingRandom = false) }
            }
        }

    @Test
    fun aFailedRandomArticleIsReportedAndCanBeTriedAgain() =
        runTest {
            wikiInfo.randomArticleResult = Result.Failure(DataError.Network())
            val viewModel = ExploreViewModel(wikiInfo)

            viewModel.test(this) {
                runOnCreate()
                expectInitialState()
                expectState { copy(versions = VersionsState.Loaded(this@ExploreViewModelTest.versions)) }

                containerHost.onAction(ExploreAction.OpenRandomArticle)

                expectState { copy(isLoadingRandom = true) }
                expectSideEffect(ExploreEffect.RandomArticleFailed)
                expectState { copy(isLoadingRandom = false) }

                containerHost.onAction(ExploreAction.OpenRandomArticle)

                expectState { copy(isLoadingRandom = true) }
                expectSideEffect(ExploreEffect.RandomArticleFailed)
                expectState { copy(isLoadingRandom = false) }
                assertThat(wikiInfo.randomArticleCalls).isEqualTo(2)
            }
        }

    @Test
    fun navigationActionsAreLeftToTheRoute() =
        runTest {
            val viewModel = ExploreViewModel(wikiInfo)

            viewModel.test(this) {
                expectInitialState()

                containerHost.onAction(ExploreAction.OpenArticle("Diamond"))
                containerHost.onAction(ExploreAction.OpenCategory("Blocks"))

                expectNoItems()
            }
        }
}
