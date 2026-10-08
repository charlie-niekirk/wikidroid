package dev.cniekirk.wikidroid.feature.settings

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.data.ArticleRepository
import dev.cniekirk.wikidroid.core.model.Article
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Edition
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.model.UserPreferences
import dev.cniekirk.wikidroid.core.testing.MainDispatcherRule
import dev.cniekirk.wikidroid.core.testing.fake.FakeArticleRepository
import dev.cniekirk.wikidroid.core.testing.fake.FakeLibraryRepository
import dev.cniekirk.wikidroid.core.testing.fake.FakeSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.orbitmvi.orbit.test.OrbitTestContext
import org.orbitmvi.orbit.test.test

/** A cache that can't be cleared, as when the disk is full or the database is locked. */
private class FailingArticles : ArticleRepository {
    override fun getArticle(title: String): Flow<Result<Article, DataError>> = emptyFlow()

    override suspend fun clearCache(): Unit = error("disk full")
}

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val library = FakeLibraryRepository()
    private val articles = FakeArticleRepository()

    private fun viewModel(articles: ArticleRepository = this.articles) = SettingsViewModel(settings, library, articles)

    private suspend fun TestScope.inSettings(
        viewModel: SettingsViewModel = viewModel(),
        body: suspend OrbitTestContext<SettingsState, Nothing, SettingsViewModel>.(state: () -> SettingsState) -> Unit,
    ) {
        viewModel.test(this) {
            runOnCreate()
            runCurrent()
            body { containerHost.container.stateFlow.value }
            cancelAndIgnoreRemainingItems()
        }
    }

    private fun TestScope.act(
        viewModel: SettingsViewModel,
        action: SettingsAction,
    ) {
        viewModel.onAction(action)
        runCurrent()
    }

    // region preferences

    @Test
    fun theSavedPreferencesAreUnknownUntilTheyAreRead() =
        runTest {
            assertThat(
                viewModel()
                    .container.stateFlow.value.preferences,
            ).isNull()
        }

    @Test
    fun showsTheSavedPreferences() =
        runTest {
            val saved =
                UserPreferences(themeMode = ThemeMode.Dark, textScale = 1.2f, preferredEdition = Edition.Bedrock)
            val viewModel = SettingsViewModel(FakeSettingsRepository(saved), library, articles)

            inSettings(viewModel) { state ->
                assertThat(state().preferences).isEqualTo(saved)
            }
        }

    @Test
    fun changingThemeIsSavedAndShown() =
        runTest {
            val viewModel = viewModel()
            inSettings(viewModel) { state ->
                act(viewModel, SettingsAction.SetThemeMode(ThemeMode.Dark))

                assertThat(settings.current.themeMode).isEqualTo(ThemeMode.Dark)
                assertThat(state().preferences?.themeMode).isEqualTo(ThemeMode.Dark)
            }
        }

    @Test
    fun dynamicColourEditionAndHistoryAreSaved() =
        runTest {
            val viewModel = viewModel()
            inSettings(viewModel) { state ->
                act(viewModel, SettingsAction.SetDynamicColor(false))
                act(viewModel, SettingsAction.SetPreferredEdition(Edition.Bedrock))
                act(viewModel, SettingsAction.SetSaveHistory(false))

                assertThat(settings.current)
                    .isEqualTo(
                        UserPreferences(dynamicColor = false, preferredEdition = Edition.Bedrock, saveHistory = false),
                    )
                assertThat(state().preferences).isEqualTo(settings.current)
            }
        }

    @Test
    fun textSizeIsSavedAndClampedToTheSupportedRange() =
        runTest {
            val viewModel = viewModel()
            inSettings(viewModel) { state ->
                act(viewModel, SettingsAction.SetTextScale(1.25f))
                assertThat(state().preferences?.textScale).isEqualTo(1.25f)

                act(viewModel, SettingsAction.SetTextScale(5f))
                assertThat(state().preferences?.textScale).isEqualTo(UserPreferences.MAX_TEXT_SCALE)
            }
        }

    @Test
    fun openingAboutIsLeftToTheRoute() =
        runTest {
            val viewModel = viewModel()
            inSettings(viewModel) { state ->
                val before = state()

                act(viewModel, SettingsAction.OpenAbout)

                assertThat(state()).isEqualTo(before)
            }
        }

    // endregion

    // region clearing

    @Test
    fun clearingAsksBeforeDeletingAnything() =
        runTest {
            library.recordVisit(ArticleSummary("Diamond"))
            val viewModel = viewModel()
            inSettings(viewModel) { state ->
                act(viewModel, SettingsAction.RequestClear(ClearTarget.History))
                assertThat(state().pendingClear).isEqualTo(ClearTarget.History)
                act(viewModel, SettingsAction.RequestClear(ClearTarget.ArticleCache))
                assertThat(state().pendingClear).isEqualTo(ClearTarget.ArticleCache)

                assertThat(library.history.first()).hasSize(1)
                assertThat(articles.clearCacheCalls).isEqualTo(0)
            }
        }

    @Test
    fun cancellingKeepsEverything() =
        runTest {
            library.recordVisit(ArticleSummary("Diamond"))
            val viewModel = viewModel()
            inSettings(viewModel) { state ->
                act(viewModel, SettingsAction.RequestClear(ClearTarget.History))

                act(viewModel, SettingsAction.DismissClear)

                assertThat(state().pendingClear).isNull()
                assertThat(state().message).isNull()
                assertThat(library.history.first()).hasSize(1)
            }
        }

    @Test
    fun confirmingClearsTheHistoryButNotTheBookmarksOrTheCache() =
        runTest {
            library.addBookmark(ArticleSummary("Diamond"))
            library.recordVisit(ArticleSummary("Diamond"))
            val viewModel = viewModel()
            inSettings(viewModel) { state ->
                act(viewModel, SettingsAction.RequestClear(ClearTarget.History))
                act(viewModel, SettingsAction.ConfirmClear)

                assertThat(library.history.first()).isEmpty()
                assertThat(library.bookmarks.first()).hasSize(1)
                assertThat(articles.clearCacheCalls).isEqualTo(0)
                assertThat(state().pendingClear).isNull()
                assertThat(state().message).isEqualTo(SettingsMessage.HistoryCleared)
            }
        }

    @Test
    fun confirmingClearsTheArticleCacheButNotTheHistory() =
        runTest {
            library.recordVisit(ArticleSummary("Diamond"))
            val viewModel = viewModel()
            inSettings(viewModel) { state ->
                act(viewModel, SettingsAction.RequestClear(ClearTarget.ArticleCache))
                act(viewModel, SettingsAction.ConfirmClear)

                assertThat(articles.clearCacheCalls).isEqualTo(1)
                assertThat(library.history.first()).hasSize(1)
                assertThat(state().pendingClear).isNull()
                assertThat(state().message).isEqualTo(SettingsMessage.CacheCleared)
            }
        }

    @Test
    fun confirmingWithNothingPendingDoesNothing() =
        runTest {
            library.recordVisit(ArticleSummary("Diamond"))
            val viewModel = viewModel()
            inSettings(viewModel) { state ->
                act(viewModel, SettingsAction.ConfirmClear)

                assertThat(articles.clearCacheCalls).isEqualTo(0)
                assertThat(library.history.first()).hasSize(1)
                assertThat(state().message).isNull()
            }
        }

    @Test
    fun aFailureToClearIsReportedNotThrown() =
        runTest {
            val viewModel = viewModel(FailingArticles())
            inSettings(viewModel) { state ->
                act(viewModel, SettingsAction.RequestClear(ClearTarget.ArticleCache))
                act(viewModel, SettingsAction.ConfirmClear)

                assertThat(state().message).isEqualTo(SettingsMessage.ClearFailed)
                assertThat(state().pendingClear).isNull()
            }
        }

    @Test
    fun theMessageIsKeptUntilTheScreenAcknowledgesIt() =
        runTest {
            val viewModel = viewModel()
            inSettings(viewModel) { state ->
                act(viewModel, SettingsAction.RequestClear(ClearTarget.ArticleCache))
                act(viewModel, SettingsAction.ConfirmClear)
                assertThat(state().message).isEqualTo(SettingsMessage.CacheCleared)

                act(viewModel, SettingsAction.MessageShown)

                assertThat(state().message).isNull()
            }
        }

    // endregion
}
