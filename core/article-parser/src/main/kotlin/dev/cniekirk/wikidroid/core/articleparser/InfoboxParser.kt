package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.InfoboxRow
import dev.cniekirk.wikidroid.core.model.RichText
import kotlinx.collections.immutable.toImmutableList
import org.jsoup.nodes.Element

/** The `div.infobox` card at the top of most articles: a title, one or more images and label/value rows. */
internal object InfoboxParser {
    fun parse(infobox: Element): ContentBlock.Infobox? {
        val title =
            infobox
                .selectFirst(".infobox-title")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        // The small inventory sprite under the main picture is decoration, not part of the article.
        val images =
            infobox
                .select(".infobox-imagearea img")
                .filter { it.closest(".infobox-invimages") == null }
                .mapNotNull(::image)
        val rows = infobox.select("table.infobox-rows").flatMap(::rowsOf)
        if (title == null && images.isEmpty() && rows.isEmpty()) return null
        return ContentBlock.Infobox(title, images.toImmutableList(), rows.toImmutableList())
    }

    // Infobox images with variants ("Normal", "Charged") sit in tabs; the tab name is the best caption.
    private fun image(img: Element): ContentBlock.Image? {
        val tabTitle = img.closest(".tabbertab")?.attr("data-title")?.takeIf { it.isNotBlank() }
        return ImageParser.image(img, tabTitle?.let(RichText::of))
    }

    private fun rowsOf(table: Element): List<InfoboxRow> =
        table
            .select("tr")
            .filter { it.closest("table") === table }
            .mapNotNull(::row)

    private fun row(tr: Element): InfoboxRow? {
        val header = tr.children().firstOrNull { it.normalName() == "th" }?.let(InlineParser::richText)
        val value = tr.children().firstOrNull { it.normalName() == "td" }?.let(InlineParser::richText)
        return when {
            header != null && value != null -> InfoboxRow(header.takeUnless { it.isBlank }, value)

            // A lone th is a section header ("Java Edition"); a lone td is a full-width value.
            else -> (value ?: header)?.let { InfoboxRow(null, it) }
        }?.takeUnless { it.value.isBlank }
    }
}
