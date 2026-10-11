package dev.cniekirk.wikidroid.feature.article

import androidx.lifecycle.ViewModel
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.data.LibraryRepository
import dev.cniekirk.wikidroid.core.data.SettingsRepository
import dev.cniekirk.wikidroid.core.domain.article.ObserveArticleUseCase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * One open article. Loads it offline-first (a saved copy shows at once and is replaced if the wiki has a newer
 * revision), records the visit in the history, and keeps the bookmark star, the reader's text size and the
 * folded sections in its state (sections start folded).
 *
 * [anchor] is the heading to scroll to once the page first appears.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@AssistedInject
class ArticleViewModel(
    @Assisted title: String,
    @Assisted private val anchor: String?,
    private val observeArticle: ObserveArticleUseCase,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) : ViewModel(),
    OrbitContainerHost<ArticleState, ArticleState, Nothing> {
    private val requestedTitle = title.trim().replace('_', ' ')

    /** Held while a download is running, so a second retry is dropped, not queued. */
    private val loadLock = Mutex()

    /** The canonical title of the page on screen, which is what bookmarks are keyed by. */
    private val shownTitle = MutableStateFlow<String?>(null)

    override val container =
        orbitContainer<ArticleState, Nothing>(ArticleState(title = requestedTitle)) {
            load()
            coroutineScope {
                launch {
                    settings.preferences
                        .map { it.textScale }
                        .distinctUntilChanged()
                        .collect { scale -> reduce { state.copy(textScale = scale) } }
                }
                launch {
                    shownTitle
                        .filterNotNull()
                        .distinctUntilChanged()
                        .flatMapLatest { library.isBookmarked(it) }
                        .collect { bookmarked -> reduce { state.copy(isBookmarked = bookmarked) } }
                }
            }
        }

    fun onAction(action: ArticleAction) {
        when (action) {
            ArticleAction.Retry -> load(onlyAfterFailure = true)

            ArticleAction.ToggleBookmark -> toggleBookmark()

            is ArticleAction.ToggleSection -> intent { reduce { state.toggleSection(action.index) } }

            ArticleAction.ShowToc -> intent { reduce { state.showToc() } }

            ArticleAction.HideToc -> intent { reduce { state.copy(isTocVisible = false) } }

            is ArticleAction.GoToAnchor -> intent { reduce { state.goToAnchor(action.anchor) } }

            ArticleAction.AnchorHandled -> intent { reduce { state.copy(pendingAnchor = null) } }

            // Navigation, sharing and the browser are the route's job.
            is ArticleAction.OpenLink,
            is ArticleAction.OpenUrl,
            ArticleAction.OpenOnWiki,
            ArticleAction.Share,
            ArticleAction.Back,
            -> Unit
        }
    }

    /** Loads the page when the screen opens, and again on retry after it failed. */
    private fun load(onlyAfterFailure: Boolean = false) =
        intent {
            if (onlyAfterFailure && state.phase !is ArticlePhase.Failed) return@intent
            if (!loadLock.tryLock()) return@intent
            try {
                reduce { state.copy(phase = ArticlePhase.Loading) }
                observeArticle(requestedTitle).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            // The anchor is for the page as it first appears, not for a refreshed copy.
                            reduce { state.withArticle(result.data, anchor.takeIf { state.article == null }) }
                            shownTitle.value = result.data.title
                        }

                        is Result.Failure -> {
                            reduce { state.withLoadFailure(result.error) }
                        }
                    }
                }
            } finally {
                loadLock.unlock()
            }
        }

    private fun toggleBookmark() =
        intent {
            val article = state.article ?: return@intent
            if (state.isBookmarked) {
                library.removeBookmark(article.title)
            } else {
                library.addBookmark(article.toSummary())
            }
        }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class, binding = binding<ManualViewModelAssistedFactory>())
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            @Assisted title: String,
            @Assisted anchor: String?,
        ): ArticleViewModel
    }
}
