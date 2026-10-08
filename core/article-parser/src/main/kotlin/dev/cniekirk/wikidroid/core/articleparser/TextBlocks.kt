package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.RichSpan
import dev.cniekirk.wikidroid.core.model.RichText
import dev.cniekirk.wikidroid.core.model.TextStyleFlag
import org.jsoup.nodes.Element

/** Blocks that are just text: paragraphs, notes, definition lists and code. */
internal object TextBlocks {
    fun paragraph(
        element: Element,
        sink: BlockSink,
    ) {
        val text = InlineParser.richText(element)
        if (!text.isBlank) {
            sink.add(ContentBlock.Paragraph(text))
            return
        }
        // A paragraph that only holds images (the wiki wraps bare [[File:]] embeds in one).
        element
            .children()
            .filter { it.isFileWrapper() }
            .mapNotNull(ImageParser::file)
            .forEach(sink::add)
    }

    fun note(
        element: Element,
        sink: BlockSink,
    ) {
        val text = InlineParser.richText(element)
        if (!text.isBlank) sink.add(ContentBlock.Note(text))
    }

    /** One tab of a tabber: its title, then whatever it contains. All tabs are shown, one after the other. */
    fun tab(
        element: Element,
        sink: BlockSink,
    ) {
        val title = element.attr("data-title").trim()
        if (title.isNotEmpty()) sink.add(ContentBlock.Paragraph(RichText.of(title).withStyle(TextStyleFlag.Bold)))
        BlockParser.parseChildren(element, sink)
    }

    fun definitions(
        list: Element,
        sink: BlockSink,
    ) {
        for (child in list.children()) {
            if (child.normalName() == "dt") {
                val term = InlineParser.richText(child)
                if (!term.isBlank) sink.add(ContentBlock.Paragraph(term.withStyle(TextStyleFlag.Bold)))
            } else {
                BlockParser.parseChildren(child, sink)
            }
        }
    }

    fun preformatted(
        element: Element,
        sink: BlockSink,
    ) {
        val text = element.wholeText().trim('\n')
        if (text.isNotBlank()) {
            sink.add(ContentBlock.Paragraph(RichText.of(RichSpan(text, setOf(TextStyleFlag.Code)))))
        }
    }
}
