package dev.cniekirk.wikidroid.feature.article

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.data.ArticleRepository
import dev.cniekirk.wikidroid.core.model.Article
import dev.cniekirk.wikidroid.core.model.UserPreferences
import dev.cniekirk.wikidroid.core.testing.MainDispatcherRule
import dev.cniekirk.wikidroid.core.testing.fake.FakeLibraryRepository
import dev.cniekirk.wikidroid.core.testing.fake.FakeSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.orbitmvi.orbit.test.OrbitTestContext
import org.orbitmvi.orbit.test.test

/**
 * The real repository's flow ends once it has said everything it will say, which is what retries depend on,
 * so the script here does the same: each call to [getArticle] plays the next list of results, then completes.
 */
private class ScriptedArticles(
    vararg script: List<Result<Article, DataError>>,
) : ArticleRepository {
    private val pending = script.toMutableList()
    val requestedTitles = mutableListOf<String>()

    override fun getArticle(title: String): Flow<Result<Article, DataError>> {
        requestedTitles += title
        val results = if (pending.isEmpty()) emptyList() else pending.removeAt(0)
        return flow { results.forEach { emit(it) } }
    }

    override suspend fun clearCache() = Unit
}

class ArticleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val settings = FakeSettingsRepository()

    private fun success(article: Article = sampleArticle()) = Result.Success(article)

    private fun viewModel(
        articles: ArticleRepository,
        title: String = "Diamond",
        anchor: String? = null,
    ) = ArticleViewModel(title, anchor, articles, library, settings)

    private suspend fun TestScope.article(
        viewModel: ArticleViewModel,
        body: suspend OrbitTestContext<ArticleState, Nothing, ArticleViewModel>.(state: () -> ArticleState) -> Unit,
    ) {
        viewModel.test(this) {
            runOnCreate()
            runCurrent()
            body { containerHost.container.stateFlow.value }
            cancelAndIgnoreRemainingItems()
        }
    }

    // region loading

    @Test
    fun startsLoadingUnderTheRequestedTitleWithSpaces() =
        runTest {
            val viewModel = viewModel(ScriptedArticles(), title = "Block_of_Diamond")

            assertThat(viewModel.container.stateFlow.value.title).isEqualTo("Block of Diamond")
            assertThat(viewModel.container.stateFlow.value.phase).isEqualTo(ArticlePhase.Loading)
        }

    @Test
    fun showsTheArticleOnceItArrives() =
        runTest {
            val repository = ScriptedArticles(listOf(success()))

            article(viewModel(repository)) { state ->
                assertThat(state().phase).isEqualTo(ArticlePhase.Loaded(sampleArticle()))
                assertThat(state().article?.title).isEqualTo("Diamond")
            }

            assertThat(repository.requestedTitles).containsExactly("Diamond")
        }

    @Test
    fun aRefreshedCopyReplacesTheCachedOne() =
        runTest {
            val cached = sampleArticle()
            val fresh = cached.copy(revisionId = 2)

            article(viewModel(ScriptedArticles(listOf(success(cached), success(fresh))))) { state ->
                assertThat(state().article?.revisionId).isEqualTo(2)
            }
        }

    @Test
    fun aFailedDownloadWithNothingToShowIsAFailure() =
        runTest {
            val error = DataError.Network()

            article(viewModel(ScriptedArticles(listOf(Result.Failure(error))))) { state ->
                assertThat(state().phase).isEqualTo(ArticlePhase.Failed(error))
                assertThat(state().article).isNull()
            }
        }

    @Test
    fun aFailureAfterTheArticleIsOnScreenIsIgnored() =
        runTest {
            val script = listOf(success(), Result.Failure(DataError.Network()))

            article(viewModel(ScriptedArticles(script))) { state ->
                assertThat(state().phase).isInstanceOf(ArticlePhase.Loaded::class.java)
            }
        }

    @Test
    fun retryDownloadsAgainAfterAFailure() =
        runTest {
            val repository = ScriptedArticles(listOf(Result.Failure(DataError.Network())), listOf(success()))
            val viewModel = viewModel(repository)

            article(viewModel) { state ->
                assertThat(state().phase).isInstanceOf(ArticlePhase.Failed::class.java)

                containerHost.onAction(ArticleAction.Retry)
                runCurrent()

                assertThat(state().phase).isInstanceOf(ArticlePhase.Loaded::class.java)
            }
            assertThat(repository.requestedTitles).hasSize(2)
        }

    @Test
    fun retryDoesNothingWhenThereIsNoFailure() =
        runTest {
            val repository = ScriptedArticles(listOf(success()), listOf(success()))

            article(viewModel(repository)) { _ ->
                containerHost.onAction(ArticleAction.Retry)
                runCurrent()
            }

            assertThat(repository.requestedTitles).hasSize(1)
        }

    // endregion

    // region history, bookmarks, preferences

    @Test
    fun recordsTheVisitInTheHistoryOnce() =
        runTest {
            val cached = sampleArticle()

            article(viewModel(ScriptedArticles(listOf(success(cached), success(cached.copy(revisionId = 2)))))) { _ ->
                val history = library.history.first()
                assertThat(history.map { it.title }).containsExactly("Diamond")
                assertThat(history.single().thumbnailUrl).isEqualTo("https://minecraft.wiki/images/Diamond.png")
            }
        }

    @Test
    fun recordsTheCanonicalTitleNotTheRedirectItWasOpenedWith() =
        runTest {
            val target = sampleArticle(title = "Diamond")

            article(viewModel(ScriptedArticles(listOf(success(target))), title = "Diamonds")) { _ ->
                assertThat(library.history.first().map { it.title }).containsExactly("Diamond")
            }
        }

    @Test
    fun recordsNothingWhenHistoryIsOff() =
        runTest {
            library.saveHistory = false

            article(viewModel(ScriptedArticles(listOf(success())))) { _ ->
                assertThat(library.history.first()).isEmpty()
            }
        }

    @Test
    fun aFailedLoadLeavesNoHistoryEntry() =
        runTest {
            article(viewModel(ScriptedArticles(listOf(Result.Failure(DataError.NotFound))))) { _ ->
                assertThat(library.history.first()).isEmpty()
            }
        }

    @Test
    fun theBookmarkToggleSavesAndRemovesTheArticle() =
        runTest {
            article(viewModel(ScriptedArticles(listOf(success())))) { state ->
                assertThat(state().isBookmarked).isFalse()

                containerHost.onAction(ArticleAction.ToggleBookmark)
                runCurrent()
                assertThat(state().isBookmarked).isTrue()
                assertThat(library.bookmarks.first().map { it.title }).containsExactly("Diamond")

                containerHost.onAction(ArticleAction.ToggleBookmark)
                runCurrent()
                assertThat(state().isBookmarked).isFalse()
                assertThat(library.bookmarks.first()).isEmpty()
            }
        }

    @Test
    fun anAlreadySavedArticleStartsBookmarked() =
        runTest {
            library.addBookmark(sampleArticle().toSummary())

            article(viewModel(ScriptedArticles(listOf(success())))) { state ->
                assertThat(state().isBookmarked).isTrue()
            }
        }

    @Test
    fun bookmarkingBeforeThePageHasLoadedDoesNothing() =
        runTest {
            article(viewModel(ScriptedArticles(listOf(Result.Failure(DataError.Network()))))) { state ->
                containerHost.onAction(ArticleAction.ToggleBookmark)
                runCurrent()

                assertThat(state().isBookmarked).isFalse()
                assertThat(library.bookmarks.first()).isEmpty()
            }
        }

    @Test
    fun followsTheTextSizePreference() =
        runTest {
            article(viewModel(ScriptedArticles(listOf(success())))) { state ->
                assertThat(state().textScale).isEqualTo(UserPreferences.DEFAULT_TEXT_SCALE)

                settings.setTextScale(1.3f)
                runCurrent()

                assertThat(state().textScale).isEqualTo(1.3f)
            }
        }

    // endregion

    // region sections, contents and anchors

    @Test
    fun foldsAndUnfoldsASection() =
        runTest {
            article(viewModel(ScriptedArticles(listOf(success())))) { state ->
                containerHost.onAction(ArticleAction.ToggleSection(1))
                runCurrent()
                assertThat(state().collapsedSections).containsExactly(1)

                containerHost.onAction(ArticleAction.ToggleSection(1))
                runCurrent()
                assertThat(state().collapsedSections).isEmpty()
            }
        }

    @Test
    fun theLeadAndUnknownSectionsDoNotFold() =
        runTest {
            article(viewModel(ScriptedArticles(listOf(success())))) { state ->
                containerHost.onAction(ArticleAction.ToggleSection(0))
                containerHost.onAction(ArticleAction.ToggleSection(42))
                runCurrent()

                assertThat(state().collapsedSections).isEmpty()
            }
        }

    @Test
    fun goingToAnAnchorUnfoldsItsSectionAndAsksForAScroll() =
        runTest {
            article(viewModel(ScriptedArticles(listOf(success())))) { state ->
                containerHost.onAction(ArticleAction.ToggleSection(1))
                containerHost.onAction(ArticleAction.ShowToc)
                runCurrent()

                containerHost.onAction(ArticleAction.GoToAnchor("Mining"))
                runCurrent()

                assertThat(state().collapsedSections).isEmpty()
                assertThat(state().pendingAnchor).isEqualTo("Mining")
                assertThat(state().isTocVisible).isFalse()
            }
        }

    @Test
    fun anAnchorThatDoesNotExistIsIgnored() =
        runTest {
            article(viewModel(ScriptedArticles(listOf(success())))) { state ->
                containerHost.onAction(ArticleAction.GoToAnchor("Nowhere"))
                runCurrent()

                assertThat(state().pendingAnchor).isNull()
            }
        }

    @Test
    fun theScreenClearsThePendingAnchorWhenItHasScrolled() =
        runTest {
            article(viewModel(ScriptedArticles(listOf(success())))) { state ->
                containerHost.onAction(ArticleAction.GoToAnchor("Crafting"))
                runCurrent()
                containerHost.onAction(ArticleAction.AnchorHandled)
                runCurrent()

                assertThat(state().pendingAnchor).isNull()
            }
        }

    @Test
    fun anAnchorFromTheKeyIsAppliedWhenThePageFirstAppears() =
        runTest {
            article(viewModel(ScriptedArticles(listOf(success())), anchor = "Mining")) { state ->
                assertThat(state().pendingAnchor).isEqualTo("Mining")
            }
        }

    @Test
    fun anAnchorFromTheKeyIsNotReappliedToARefreshedCopy() =
        runTest {
            val cached = sampleArticle()
            val refreshGate = CompletableDeferred<Unit>()
            val repository =
                object : ArticleRepository {
                    override fun getArticle(title: String) =
                        flow<Result<Article, DataError>> {
                            emit(success(cached))
                            refreshGate.await()
                            emit(success(cached.copy(revisionId = 2)))
                        }

                    override suspend fun clearCache() = Unit
                }

            article(viewModel(repository, anchor = "Mining")) { state ->
                assertThat(state().pendingAnchor).isEqualTo("Mining")
                // The reader has scrolled on and the screen cleared it; a refresh must not drag them back.
                containerHost.onAction(ArticleAction.AnchorHandled)
                runCurrent()

                refreshGate.complete(Unit)
                runCurrent()

                assertThat(state().article?.revisionId).isEqualTo(2)
                assertThat(state().pendingAnchor).isNull()
            }
        }

    @Test
    fun theContentsSheetNeedsAnArticle() =
        runTest {
            article(viewModel(ScriptedArticles(listOf(Result.Failure(DataError.Network()))))) { state ->
                containerHost.onAction(ArticleAction.ShowToc)
                runCurrent()

                assertThat(state().isTocVisible).isFalse()
            }
        }

    @Test
    fun theContentsSheetOpensAndCloses() =
        runTest {
            article(viewModel(ScriptedArticles(listOf(success())))) { state ->
                containerHost.onAction(ArticleAction.ShowToc)
                runCurrent()
                assertThat(state().isTocVisible).isTrue()

                containerHost.onAction(ArticleAction.HideToc)
                runCurrent()
                assertThat(state().isTocVisible).isFalse()
            }
        }

    // endregion

    @Test
    fun navigationActionsAreLeftToTheRoute() =
        runTest {
            article(viewModel(ScriptedArticles(listOf(success())))) { state ->
                val before = state()

                containerHost.onAction(ArticleAction.Back)
                containerHost.onAction(ArticleAction.Share)
                containerHost.onAction(ArticleAction.OpenOnWiki)
                containerHost.onAction(ArticleAction.OpenUrl("https://minecraft.wiki"))
                runCurrent()

                assertThat(state()).isEqualTo(before)
            }
        }
}
