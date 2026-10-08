package dev.cniekirk.wikidroid.core.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class Article(
    val title: String,
    val displayTitle: String,
    val revisionId: Long,
    val sections: ImmutableList<ArticleSection>,
    val categories: ImmutableList<String> = persistentListOf(),
    val pageUrl: String? = null,
    /** A representative image (the infobox picture, else the first lead image) for history and bookmark rows. */
    val thumbnailUrl: String? = null,
) {
    /** Every heading in reading order, for the table-of-contents sheet. */
    val tableOfContents: List<ContentBlock.Heading>
        get() =
            sections.flatMap { section ->
                listOfNotNull(section.heading) + section.blocks.filterIsInstance<ContentBlock.Heading>()
            }

    /** This article as it appears in a list, which is what [LibraryEntry] rows are built from. */
    fun toSummary(): ArticleSummary =
        ArticleSummary(title = title, displayTitle = displayTitle, thumbnailUrl = thumbnailUrl, pageUrl = pageUrl)
}

/**
 * One collapsible section of an article. The lead section has no [heading].
 * Deeper headings stay in [blocks] as [ContentBlock.Heading].
 */
@Immutable
data class ArticleSection(
    val heading: ContentBlock.Heading?,
    val blocks: ImmutableList<ContentBlock>,
)
