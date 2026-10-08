package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.ListItem
import dev.cniekirk.wikidroid.core.model.RichText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jsoup.nodes.Element

internal object ListParser {
    private val LIST_TAGS = setOf("ul", "ol")

    fun parse(list: Element): ContentBlock.ListBlock? {
        val items = mutableListOf<ListItem>()
        for (child in list.children()) {
            when (child.normalName()) {
                "li" -> items += item(child)

                // Markup like <ul><li/><ul/></ul> nests the list directly; it belongs to the preceding item.
                in LIST_TAGS -> parse(child)?.let { items.addToLast(it) }
            }
        }
        val visible = items.filter { !it.text.isBlank || it.sublists.isNotEmpty() }
        return visible.takeIf { it.isNotEmpty() }?.let {
            ContentBlock.ListBlock(ordered = list.normalName() == "ol", items = it.toImmutableList())
        }
    }

    private fun item(li: Element): ListItem {
        val text = InlineParser.richText(li.childNodes().filterNot { it is Element && it.normalName() in LIST_TAGS })
        val sublists = li.children().filter { it.normalName() in LIST_TAGS }.mapNotNull(::parse)
        return ListItem(text, sublists.toImmutableList())
    }

    private fun MutableList<ListItem>.addToLast(sublist: ContentBlock.ListBlock) {
        val previous = lastOrNull()
        if (previous == null) {
            add(ListItem(RichText.Empty, persistentListOf(sublist)))
        } else {
            this[lastIndex] = previous.copy(sublists = (previous.sublists + sublist).toImmutableList())
        }
    }
}
