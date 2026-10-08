package dev.cniekirk.wikidroid.feature.article

import androidx.compose.runtime.Immutable
import dev.cniekirk.wikidroid.core.model.Article
import dev.cniekirk.wikidroid.core.model.ContentBlock
import java.net.URLDecoder

/** One row of the article's lazy list. [key] is stable across edits to the folded sections. */
@Immutable
internal sealed interface ArticleListItem {
    val key: String

    /** A section's `h2`, which folds and unfolds its [sectionIndex]th section. */
    data class SectionHeader(
        val sectionIndex: Int,
        val heading: ContentBlock.Heading,
        val isCollapsed: Boolean,
    ) : ArticleListItem {
        override val key: String get() = "section-$sectionIndex"
    }

    data class Block(
        val sectionIndex: Int,
        val blockIndex: Int,
        val block: ContentBlock,
    ) : ArticleListItem {
        override val key: String get() = "block-$sectionIndex-$blockIndex"
    }

    /** The CC BY-NC-SA credit at the very end. */
    data object Footer : ArticleListItem {
        override val key: String get() = "footer"
    }
}

/** An article flattened into list rows, with the position of every visible heading. */
@Immutable
internal class ArticleLayout(
    val items: List<ArticleListItem>,
) {
    /** The row index of the heading with exactly this id, or `null` when it is hidden inside a folded section. */
    fun indexOfAnchor(anchor: String): Int? =
        items
            .indexOfFirst { item ->
                when (item) {
                    is ArticleListItem.SectionHeader -> item.heading.anchor == anchor
                    is ArticleListItem.Block -> (item.block as? ContentBlock.Heading)?.anchor == anchor
                    ArticleListItem.Footer -> false
                }
            }.takeIf { it >= 0 }
}

/** Blocks of every section in order, except those in a folded section, which keeps only its header. */
internal fun buildArticleLayout(
    article: Article,
    collapsedSections: Set<Int>,
): ArticleLayout {
    val items = mutableListOf<ArticleListItem>()
    article.sections.forEachIndexed { sectionIndex, section ->
        val heading = section.heading
        val collapsed = heading != null && sectionIndex in collapsedSections
        if (heading != null) items += ArticleListItem.SectionHeader(sectionIndex, heading, collapsed)
        if (!collapsed) {
            section.blocks.forEachIndexed { blockIndex, block ->
                items += ArticleListItem.Block(sectionIndex, blockIndex, block)
            }
        }
    }
    items += ArticleListItem.Footer
    return ArticleLayout(items)
}

/** Where a heading lives: its section, and the id exactly as the article spells it. */
internal data class AnchorLocation(
    val sectionIndex: Int,
    val anchor: String,
)

/**
 * Finds the heading that [anchor] names. Links spell ids in several ways (`Spawn_rates`, `Spawn rates`,
 * percent-encoded), so an exact match is preferred and a normalised one is accepted.
 */
internal fun Article.locate(anchor: String): AnchorLocation? {
    val headings =
        sections.flatMapIndexed { index, section ->
            listOfNotNull(section.heading).map { index to it.anchor } +
                section.blocks.filterIsInstance<ContentBlock.Heading>().map { index to it.anchor }
        }
    val match =
        headings.firstOrNull { it.second == anchor }
            ?: headings.firstOrNull { it.second.normalisedAnchor() == anchor.normalisedAnchor() }
    return match?.let { (sectionIndex, id) -> AnchorLocation(sectionIndex, id) }
}

private fun String.normalisedAnchor(): String {
    val decoded = runCatching { URLDecoder.decode(replace("+", "%2B"), "UTF-8") }.getOrDefault(this)
    return decoded.trim().replace(' ', '_').lowercase()
}
