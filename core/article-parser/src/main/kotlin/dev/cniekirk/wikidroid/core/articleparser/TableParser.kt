package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.TableCell
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jsoup.nodes.Element

internal object TableParser {
    private val ROW_GROUPS = setOf("thead", "tbody", "tfoot")

    fun parse(table: Element): ContentBlock.Table? {
        val rows = rowsOf(table).map(::cellsOf).filter { it.isNotEmpty() }
        if (rows.isEmpty()) return null
        val caption = table.children().firstOrNull { it.normalName() == "caption" }?.let(InlineParser::richText)
        return ContentBlock.Table(
            caption = caption?.takeUnless { it.isBlank },
            rows = rows.map { it.toImmutableList() }.toImmutableList(),
        )
    }

    /** Only this table's own rows: the browser nests `<tbody>` implicitly, and cells can hold tables of their own. */
    private fun rowsOf(table: Element): List<Element> =
        table.children().flatMap { child ->
            when (child.normalName()) {
                "tr" -> listOf(child)
                in ROW_GROUPS -> child.children().filter { it.normalName() == "tr" }
                else -> emptyList()
            }
        }

    private fun cellsOf(row: Element): ImmutableList<TableCell> =
        row
            .children()
            .filter { it.normalName() == "td" || it.normalName() == "th" }
            .map { cell ->
                TableCell(
                    content = InlineParser.richText(cell),
                    isHeader = cell.normalName() == "th",
                    colSpan = cell.intAttr("colspan") ?: 1,
                    rowSpan = cell.intAttr("rowspan") ?: 1,
                )
            }.toImmutableList()
}
