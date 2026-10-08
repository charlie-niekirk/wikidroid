package dev.cniekirk.wikidroid.feature.article

import dev.cniekirk.wikidroid.core.model.Article
import dev.cniekirk.wikidroid.core.model.ArticleSection
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.model.RichSpan
import dev.cniekirk.wikidroid.core.model.RichText
import kotlinx.collections.immutable.persistentListOf

internal fun text(value: String): RichText = RichText.of(value)

internal fun paragraph(value: String) = ContentBlock.Paragraph(text(value))

internal fun linked(
    label: String,
    link: Link,
): RichText = RichText.of(RichSpan(label, link = link))

internal fun heading(
    text: String,
    level: Int = 2,
    anchor: String = text.replace(' ', '_'),
) = ContentBlock.Heading(level, text, anchor)

/**
 * A lead, then "Obtaining" (with a nested "Mining"), then "Crafting".
 * Section indices: 0 lead, 1 Obtaining, 2 Crafting.
 */
internal fun sampleArticle(
    title: String = "Diamond",
    pageUrl: String? = "https://minecraft.wiki/w/Diamond",
): Article =
    Article(
        title = title,
        displayTitle = title,
        revisionId = 1,
        thumbnailUrl = "https://minecraft.wiki/images/Diamond.png",
        pageUrl = pageUrl,
        sections =
            persistentListOf(
                ArticleSection(null, persistentListOf(paragraph("A diamond is a mineral."))),
                ArticleSection(
                    heading("Obtaining"),
                    persistentListOf(
                        paragraph("Found underground."),
                        heading("Mining", level = 3),
                        paragraph("Use an iron pickaxe."),
                    ),
                ),
                ArticleSection(heading("Crafting"), persistentListOf(paragraph("Made from a block."))),
            ),
    )
