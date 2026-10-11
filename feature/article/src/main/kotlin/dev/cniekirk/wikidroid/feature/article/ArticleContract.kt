package dev.cniekirk.wikidroid.feature.article

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.model.Article
import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.model.UserPreferences
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet

/**
 * One article on screen. [title] is the title it was opened with and is what the top bar shows until the
 * page arrives. [phase] holds the page itself; a refreshed copy replaces it in place.
 */
@Immutable
data class ArticleState(
    val title: String,
    val phase: ArticlePhase = ArticlePhase.Loading,
    val isBookmarked: Boolean = false,
    /** Indices into [Article.sections] that are folded. Sections start folded; the lead section never folds. */
    val collapsedSections: ImmutableSet<Int> = persistentSetOf(),
    val textScale: Float = UserPreferences.DEFAULT_TEXT_SCALE,
    val isTocVisible: Boolean = false,
    /** A heading id to scroll to once the page is laid out. Cleared by [ArticleAction.AnchorHandled]. */
    val pendingAnchor: String? = null,
) {
    val article: Article? get() = (phase as? ArticlePhase.Loaded)?.article

    /**
     * Shows [article], then goes to [anchor] if there is one. Sections fold by default, so every headed section
     * the reader has not seen before starts folded; on a refreshed copy the reader's own choices for the
     * sections they already had are kept.
     */
    fun withArticle(
        article: Article,
        anchor: String? = null,
    ): ArticleState {
        val alreadyKnown = this.article?.sections?.size ?: 0
        val added = article.sections.indices.filter { it >= alreadyKnown && article.sections[it].heading != null }
        val shown =
            copy(
                phase = ArticlePhase.Loaded(article),
                collapsedSections = (collapsedSections + added).toImmutableSet(),
            )
        return if (anchor == null) shown else shown.goToAnchor(anchor)
    }

    /** Once a page is on screen a failed refresh must not replace it. */
    fun withLoadFailure(error: DataError): ArticleState =
        if (article != null) this else copy(phase = ArticlePhase.Failed(error))

    fun toggleSection(index: Int): ArticleState {
        val section = article?.sections?.getOrNull(index)
        // The lead has no heading to tap, and a section that doesn't exist can't fold.
        if (section?.heading == null) return this
        val folded = if (index in collapsedSections) collapsedSections - index else collapsedSections + index
        return copy(collapsedSections = folded.toImmutableSet())
    }

    /** Unfolds the section holding [anchor] and asks the screen to scroll there. An unknown anchor is ignored. */
    fun goToAnchor(anchor: String): ArticleState {
        val location = article?.locate(anchor) ?: return this
        return copy(
            collapsedSections = (collapsedSections - location.sectionIndex).toImmutableSet(),
            pendingAnchor = location.anchor,
            isTocVisible = false,
        )
    }

    /** There is no contents list before the page arrives. */
    fun showToc(): ArticleState = if (article == null) this else copy(isTocVisible = true)
}

@Immutable
sealed interface ArticlePhase {
    data object Loading : ArticlePhase

    data class Loaded(
        val article: Article,
    ) : ArticlePhase

    data class Failed(
        val error: DataError,
    ) : ArticlePhase
}

/**
 * [OpenLink], [OpenUrl], [OpenOnWiki], [Share] and [Back] need a `Context` or the navigator, so the route
 * handles them and the ViewModel ignores them.
 */
sealed interface ArticleAction {
    /** Retries the download after it failed. */
    data object Retry : ArticleAction

    data object ToggleBookmark : ArticleAction

    /** Folds or unfolds the section at [index] in [Article.sections]. */
    data class ToggleSection(
        val index: Int,
    ) : ArticleAction

    data object ShowToc : ArticleAction

    data object HideToc : ArticleAction

    /** Scrolls to the heading whose id is [anchor], unfolding its section first. */
    data class GoToAnchor(
        val anchor: String,
    ) : ArticleAction

    /** The screen has scrolled to [ArticleState.pendingAnchor]. */
    data object AnchorHandled : ArticleAction

    data class OpenLink(
        val link: Link,
    ) : ArticleAction

    /** Opens any http(s) address, such as the attribution footer's links. */
    data class OpenUrl(
        val url: String,
    ) : ArticleAction

    data object OpenOnWiki : ArticleAction

    data object Share : ArticleAction

    data object Back : ArticleAction
}
