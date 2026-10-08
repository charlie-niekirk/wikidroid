package dev.cniekirk.wikidroid.feature.article

import androidx.lifecycle.ViewModel
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.dataResultOf
import dev.cniekirk.wikidroid.core.data.ArticleRepository
import dev.cniekirk.wikidroid.core.data.LibraryRepository
import dev.cniekirk.wikidroid.core.data.SettingsRepository
import dev.cniekirk.wikidroid.core.model.Article
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.collections.immutable.toImmutableSet
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
import org.orbitmvi.orbit.syntax.Syntax
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * One open article. Loads it offline-first (a saved copy shows at once and is replaced if the wiki has a newer
 * revision), records the visit in the history, and keeps the bookmark star, the reader's text size and the
 * folded sections in its state.
 *
 * [anchor] is the heading to scroll to once the page first appears.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@AssistedInject
class ArticleViewModel(
    @Assisted title: String,
    @Assisted private val anchor: String?,
    private val articles: ArticleRepository,
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
                launch { load() }
            }
        }

    fun onAction(action: ArticleAction) {
        when (action) {
            ArticleAction.Retry -> {
                retry()
            }

            ArticleAction.ToggleBookmark -> {
                toggleBookmark()
            }

            is ArticleAction.ToggleSection -> {
                intent { reduce { state.toggleSection(action.index) } }
            }

            ArticleAction.ShowToc -> {
                intent {
                    reduce {
                        if (state.article ==
                            null
                        ) {
                            state
                        } else {
                            state.copy(isTocVisible = true)
                        }
                    }
                }
            }

            ArticleAction.HideToc -> {
                intent { reduce { state.copy(isTocVisible = false) } }
            }

            is ArticleAction.GoToAnchor -> {
                intent { reduce { state.goToAnchor(action.anchor) } }
            }

            ArticleAction.AnchorHandled -> {
                intent { reduce { state.copy(pendingAnchor = null) } }
            }

            // Navigation, sharing and the browser are the route's job.
            is ArticleAction.OpenLink,
            is ArticleAction.OpenUrl,
            ArticleAction.OpenOnWiki,
            ArticleAction.Share,
            ArticleAction.Back,
            -> {
                // Nothing to do here.
            }
        }
    }

    private fun retry() =
        intent {
            if (state.phase is ArticlePhase.Failed) load()
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

    private suspend fun Syntax<ArticleState, Nothing>.load() {
        if (!loadLock.tryLock()) return
        try {
            reduce { state.copy(phase = ArticlePhase.Loading) }
            var firstArticle = true
            articles.getArticle(requestedTitle).collect { result ->
                when (result) {
                    is Result.Success -> {
                        show(result.data, isFirst = firstArticle)
                        firstArticle = false
                    }

                    // Once a page is on screen a failed refresh must not replace it.
                    is Result.Failure -> {
                        reduce {
                            if (state.article !=
                                null
                            ) {
                                state
                            } else {
                                state.copy(phase = ArticlePhase.Failed(result.error))
                            }
                        }
                    }
                }
            }
        } finally {
            loadLock.unlock()
        }
    }

    private suspend fun Syntax<ArticleState, Nothing>.show(
        article: Article,
        isFirst: Boolean,
    ) {
        reduce { state.copy(phase = ArticlePhase.Loaded(article)) }
        shownTitle.value = article.title
        if (!isFirst) return
        // A history write that fails must not take the page down with it.
        dataResultOf { library.recordVisit(article.toSummary()) }
        if (anchor != null) reduce { state.goToAnchor(anchor) }
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

private fun ArticleState.toggleSection(index: Int): ArticleState {
    val section = article?.sections?.getOrNull(index)
    // The lead has no heading to tap, and a section that doesn't exist can't fold.
    if (section?.heading == null) return this
    val folded = if (index in collapsedSections) collapsedSections - index else collapsedSections + index
    return copy(collapsedSections = folded.toImmutableSet())
}

/** Unfolds the section holding [anchor] and asks the screen to scroll there. An unknown anchor is ignored. */
private fun ArticleState.goToAnchor(anchor: String): ArticleState {
    val location = article?.locate(anchor) ?: return this
    return copy(
        collapsedSections = (collapsedSections - location.sectionIndex).toImmutableSet(),
        pendingAnchor = location.anchor,
        isTocVisible = false,
    )
}
