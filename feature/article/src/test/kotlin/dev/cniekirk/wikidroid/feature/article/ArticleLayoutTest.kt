package dev.cniekirk.wikidroid.feature.article

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class ArticleLayoutTest {
    private val article = sampleArticle()

    private fun ArticleLayout.keys() = items.map { it.key }

    @Test
    fun listsEveryBlockWithSectionHeadersAndAFooter() {
        val layout = buildArticleLayout(article, collapsedSections = emptySet())

        assertThat(layout.keys())
            .containsExactly(
                "block-0-0",
                "section-1",
                "block-1-0",
                "block-1-1",
                "block-1-2",
                "section-2",
                "block-2-0",
                "footer",
            ).inOrder()
    }

    @Test
    fun aFoldedSectionKeepsOnlyItsHeader() {
        val layout = buildArticleLayout(article, collapsedSections = setOf(1))

        assertThat(
            layout.keys(),
        ).containsExactly("block-0-0", "section-1", "section-2", "block-2-0", "footer").inOrder()
        val header = layout.items.filterIsInstance<ArticleListItem.SectionHeader>().first()
        assertThat(header.isCollapsed).isTrue()
    }

    @Test
    fun theLeadCannotBeFolded() {
        val layout = buildArticleLayout(article, collapsedSections = setOf(0))

        assertThat(layout.keys()).contains("block-0-0")
    }

    @Test
    fun findsTheRowOfAVisibleHeading() {
        val layout = buildArticleLayout(article, collapsedSections = emptySet())

        assertThat(layout.indexOfAnchor("Obtaining")).isEqualTo(1)
        assertThat(layout.indexOfAnchor("Mining")).isEqualTo(3)
        assertThat(layout.indexOfAnchor("Crafting")).isEqualTo(5)
        assertThat(layout.indexOfAnchor("Missing")).isNull()
    }

    @Test
    fun aHeadingInsideAFoldedSectionHasNoRow() {
        val layout = buildArticleLayout(article, collapsedSections = setOf(1))

        assertThat(layout.indexOfAnchor("Mining")).isNull()
        assertThat(layout.indexOfAnchor("Obtaining")).isEqualTo(1)
    }

    @Test
    fun keysAreUniqueEvenWhenASectionRepeats() {
        val repeated = article.copy(sections = (article.sections + article.sections).toImmutableList())

        val keys = buildArticleLayout(repeated, emptySet()).keys()

        assertThat(keys).containsNoDuplicates()
    }

    // region locating anchors

    @Test
    fun locatesAnExactAnchorInItsSection() {
        assertThat(article.locate("Mining")).isEqualTo(AnchorLocation(sectionIndex = 1, anchor = "Mining"))
        assertThat(article.locate("Crafting")).isEqualTo(AnchorLocation(sectionIndex = 2, anchor = "Crafting"))
    }

    @Test
    fun acceptsTheOtherSpellingsOfAnAnchor() {
        val spawn =
            article.copy(
                sections =
                    persistentListOf(
                        article.sections[0],
                        article.sections[1].copy(heading = heading("Spawn rates", anchor = "Spawn_rates")),
                    ),
            )

        assertThat(spawn.locate("Spawn rates")?.anchor).isEqualTo("Spawn_rates")
        assertThat(spawn.locate("spawn_rates")?.anchor).isEqualTo("Spawn_rates")
        assertThat(spawn.locate("Spawn%20rates")?.anchor).isEqualTo("Spawn_rates")
        assertThat(spawn.locate("Nothing")).isNull()
    }
}
