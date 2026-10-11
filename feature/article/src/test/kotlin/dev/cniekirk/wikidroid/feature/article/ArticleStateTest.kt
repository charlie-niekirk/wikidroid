package dev.cniekirk.wikidroid.feature.article

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import org.junit.Test

class ArticleStateTest {
    private val opening = ArticleState(title = "Diamond")
    private val shown = opening.withArticle(sampleArticle())

    @Test
    fun aNewPageStartsWithEveryHeadedSectionFolded() {
        assertThat(shown.article).isEqualTo(sampleArticle())
        assertThat(shown.collapsedSections).containsExactly(1, 2)
        assertThat(shown.pendingAnchor).isNull()
    }

    @Test
    fun anAnchorOpensItsSectionAndAsksForAScroll() {
        val anchored = opening.withArticle(sampleArticle(), anchor = "Mining")

        assertThat(anchored.collapsedSections).containsExactly(2)
        assertThat(anchored.pendingAnchor).isEqualTo("Mining")
    }

    @Test
    fun aFailureReplacesTheSpinnerButNotAShownPage() {
        assertThat(
            opening.withLoadFailure(DataError.Network()).phase,
        ).isEqualTo(ArticlePhase.Failed(DataError.Network()))
        assertThat(shown.withLoadFailure(DataError.Network())).isEqualTo(shown)
    }

    @Test
    fun theContentsListNeedsAPage() {
        assertThat(opening.showToc().isTocVisible).isFalse()
        assertThat(shown.showToc().isTocVisible).isTrue()
    }

    @Test
    fun theLeadCannotFold() {
        assertThat(shown.toggleSection(0)).isEqualTo(shown)
        assertThat(shown.toggleSection(1).collapsedSections).containsExactly(2)
    }
}
