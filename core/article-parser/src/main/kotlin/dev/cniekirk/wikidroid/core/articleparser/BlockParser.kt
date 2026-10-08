package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.RichText
import dev.cniekirk.wikidroid.core.model.TextStyleFlag
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Receives parsed blocks in reading order. A level-2 heading starts a new [ArticleSection]. */
internal interface BlockSink {
    fun add(block: ContentBlock)

    fun startSection(heading: ContentBlock.Heading)
}

/**
 * Maps the children of a container to [ContentBlock]s. Known constructs get their own block type, unknown
 * containers are looked through, and widgets that need JavaScript or a wiki page become [ContentBlock.Unsupported].
 */
internal object BlockParser {
    private const val SNIPPET_LENGTH = 1_000

    private val INLINE_TAGS =
        setOf(
            "a",
            "abbr",
            "b",
            "bdi",
            "bdo",
            "big",
            "cite",
            "code",
            "data",
            "del",
            "dfn",
            "em",
            "font",
            "i",
            "img",
            "ins",
            "kbd",
            "mark",
            "q",
            "s",
            "samp",
            "small",
            "span",
            "strike",
            "strong",
            "sub",
            "sup",
            "time",
            "tt",
            "u",
            "var",
            "wbr",
            "br",
        )
    private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val UNSUPPORTED_TAGS = setOf("iframe", "video", "audio", "canvas", "form", "svg", "math", "object", "embed")
    private val UNSUPPORTED_CLASSES = setOf("embedvideo", "issue-list", "calculator-container", "load-page", "treeview")
    private const val CALCULATOR_PREFIX = "mcw-calc"
    private const val SECTION_LEVEL = 2

    fun parseChildren(
        parent: Element,
        sink: BlockSink,
    ) {
        val pending = mutableListOf<Node>()
        for (node in parent.childNodes()) {
            when {
                node is TextNode || (node is Element && isInline(node)) -> {
                    pending += node
                }

                node is Element -> {
                    flushInline(pending, sink)
                    parseElement(node, sink)
                }
                // Comments and the like neither add content nor end a run of text.
            }
        }
        flushInline(pending, sink)
    }

    private fun flushInline(
        pending: MutableList<Node>,
        sink: BlockSink,
    ) {
        if (pending.isEmpty()) return
        val text = InlineParser.richText(pending)
        pending.clear()
        if (!text.isBlank) sink.add(ContentBlock.Paragraph(text))
    }

    private fun isInline(element: Element): Boolean =
        element.normalName() in INLINE_TAGS && !element.hasClass("mcui") && !element.isFileWrapper()

    private fun parseElement(
        element: Element,
        sink: BlockSink,
    ) {
        val tag = element.normalName()
        when {
            element.isHidden() -> Unit
            element.hasClass("mw-heading") -> element.selectFirst("h1, h2, h3, h4, h5, h6")?.let { heading(it, sink) }
            tag in HEADING_TAGS -> heading(element, sink)
            isUnsupported(element) -> sink.add(unsupported(element))
            else -> parseContent(element, tag, sink)
        }
    }

    private fun parseContent(
        element: Element,
        tag: String,
        sink: BlockSink,
    ) {
        when {
            element.hasClass("infobox") -> {
                InfoboxParser.parse(element)?.let(sink::add)
            }

            element.hasClass("hatnote") || element.hasClass("msgbox") || element.attr("role") == "note" -> {
                TextBlocks.note(element, sink)
            }

            element.hasClass("mcui-Crafting_Table") -> {
                sink.add(CraftingGridParser.grid(element))
            }

            element.hasClass("tabbertab") -> {
                TextBlocks.tab(element, sink)
            }

            element.isFileWrapper() -> {
                file(element, tag, sink)
            }

            else -> {
                parseText(element, tag, sink)
            }
        }
    }

    private fun parseText(
        element: Element,
        tag: String,
        sink: BlockSink,
    ) {
        when {
            tag == "p" -> TextBlocks.paragraph(element, sink)
            tag == "ul" && element.hasClass("gallery") -> ImageParser.gallery(element)?.let(sink::add)
            tag == "ul" || tag == "ol" -> ListParser.parse(element)?.let(sink::add)
            tag == "dl" -> TextBlocks.definitions(element, sink)
            tag == "table" -> TableParser.parse(element)?.let(sink::add)
            tag == "pre" -> TextBlocks.preformatted(element, sink)
            else -> parseChildren(element, sink)
        }
    }

    private fun file(
        element: Element,
        tag: String,
        sink: BlockSink,
    ) {
        if (tag == "figure") {
            val image = ImageParser.figure(element)
            if (image != null) sink.add(image) else parseChildren(element, sink)
        } else {
            ImageParser.file(element)?.let(sink::add)
        }
    }

    private fun isUnsupported(element: Element): Boolean =
        element.normalName() in UNSUPPORTED_TAGS ||
            UNSUPPORTED_CLASSES.any(element::hasClass) ||
            element.classNames().any { it.startsWith(CALCULATOR_PREFIX) } ||
            (element.hasClass("mcui") && !element.hasClass("mcui-Crafting_Table"))

    private fun unsupported(element: Element) = ContentBlock.Unsupported(element.outerHtml().take(SNIPPET_LENGTH))

    private fun heading(
        element: Element,
        sink: BlockSink,
    ) {
        val text = element.text().trim()
        if (text.isEmpty()) return
        val anchor =
            element
                .id()
                .ifEmpty {
                    element
                        .selectFirst(
                            "[id]",
                        )?.id()
                        .orEmpty()
                }.ifEmpty { text.replace(' ', '_') }
        val level = element.normalName().removePrefix("h").toIntOrNull() ?: SECTION_LEVEL
        val heading = ContentBlock.Heading(level, text, anchor)
        if (level <= SECTION_LEVEL) sink.startSection(heading) else sink.add(heading)
    }
}
