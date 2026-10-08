package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.model.RichText
import dev.cniekirk.wikidroid.core.model.TextStyleFlag
import kotlinx.collections.immutable.toImmutableList
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Flattens inline markup into [RichText]: styles, links, line breaks and inline images survive. */
internal object InlineParser {
    private val STYLES =
        mapOf(
            "b" to TextStyleFlag.Bold,
            "strong" to TextStyleFlag.Bold,
            "i" to TextStyleFlag.Italic,
            "em" to TextStyleFlag.Italic,
            "cite" to TextStyleFlag.Italic,
            "dfn" to TextStyleFlag.Italic,
            "var" to TextStyleFlag.Italic,
            "u" to TextStyleFlag.Underline,
            "ins" to TextStyleFlag.Underline,
            "s" to TextStyleFlag.Strikethrough,
            "strike" to TextStyleFlag.Strikethrough,
            "del" to TextStyleFlag.Strikethrough,
            "code" to TextStyleFlag.Code,
            "tt" to TextStyleFlag.Code,
            "kbd" to TextStyleFlag.Code,
            "samp" to TextStyleFlag.Code,
            "sup" to TextStyleFlag.Superscript,
            "sub" to TextStyleFlag.Subscript,
            "h1" to TextStyleFlag.Bold,
            "h2" to TextStyleFlag.Bold,
            "h3" to TextStyleFlag.Bold,
            "h4" to TextStyleFlag.Bold,
            "h5" to TextStyleFlag.Bold,
            "h6" to TextStyleFlag.Bold,
        )

    /** Elements that start and end a line when they appear inside running text, such as in a table cell. */
    private val BREAKING_TAGS =
        setOf(
            "p",
            "div",
            "ul",
            "ol",
            "li",
            "dl",
            "dt",
            "dd",
            "table",
            "tr",
            "caption",
            "blockquote",
            "figure",
            "figcaption",
            "pre",
            "section",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
        )
    private val CELL_TAGS = setOf("td", "th")

    fun richText(element: Element): RichText = richText(element.childNodes())

    fun richText(nodes: List<Node>): RichText {
        val out = RichTextBuilder()
        nodes.forEach { walk(it, emptySet(), null, out) }
        return out.build()
    }

    private fun walk(
        node: Node,
        styles: Set<TextStyleFlag>,
        link: Link?,
        out: RichTextBuilder,
    ) {
        when (node) {
            is TextNode -> out.append(node.wholeText, styles, link)
            is Element -> walkElement(node, styles, link, out)
        }
    }

    private fun walkElement(
        element: Element,
        styles: Set<TextStyleFlag>,
        link: Link?,
        out: RichTextBuilder,
    ) {
        val tag = element.normalName()
        when {
            element.isHidden() -> {
                Unit
            }

            tag == "br" -> {
                out.lineBreak()
            }

            tag == "img" -> {
                image(element, styles, link, out)
            }

            element.hasClass("mcui") -> {
                out.append(CraftingGridParser.summary(element), styles, link)
            }

            element.hasClass("invslot") -> {
                CraftingGridParser.slot(element)?.let {
                    out.append(CraftingGridParser.label(it), styles, link)
                }
            }

            else -> {
                walkContainer(element, tag, styles, link, out)
            }
        }
    }

    private fun walkContainer(
        element: Element,
        tag: String,
        styles: Set<TextStyleFlag>,
        link: Link?,
        out: RichTextBuilder,
    ) {
        val childStyles = STYLES[tag]?.let { styles + it } ?: styles
        val childLink = if (tag == "a") WikiUrls.link(element) else link
        val breaking = tag in BREAKING_TAGS
        if (breaking) out.blockBoundary()
        if (tag == "li") out.append("• ")
        element.childNodes().forEach { walk(it, childStyles, childLink, out) }
        if (breaking) out.blockBoundary()
        if (tag in CELL_TAGS) out.append(" ")
    }

    private fun image(
        img: Element,
        styles: Set<TextStyleFlag>,
        link: Link?,
        out: RichTextBuilder,
    ) {
        val alt = img.attr("alt").trim()
        when {
            // Health and hunger bars are rows of tiny images whose alt text is the emoji ("❤️ × 10"); keep those.
            img.closest(".iconbar") != null -> if (alt.isNotEmpty()) out.append(alt, styles, link)

            // The wiki's own hatnote decoration; the app draws its own icon on a note.
            img.closest(".hatnote") != null -> Unit

            else -> ImageParser.inline(img)?.let { out.appendImage(it, alt, styles, link) }
        }
    }
}

/** The same text with [flag] added to every span, for things like definition terms. */
internal fun RichText.withStyle(flag: TextStyleFlag): RichText =
    RichText(spans.map { it.copy(styles = it.styles + flag) }.toImmutableList())
