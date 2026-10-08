package dev.cniekirk.wikidroid.feature.article.render

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.model.TableCell

/** A cell and the grid squares it covers. [rowSpan] and [colSpan] are already clamped to the table. */
internal data class PlacedCell(
    val cell: TableCell,
    val row: Int,
    val column: Int,
    val rowSpan: Int,
    val colSpan: Int,
)

internal data class TablePlacement(
    val cells: List<PlacedCell>,
    val rowCount: Int,
    val columnCount: Int,
)

/**
 * Applies the HTML table rules for `colspan` and `rowspan`: each cell takes the first column of its row that
 * no cell from a row above is still covering, so the cells after a `rowspan` cell stay in their columns.
 */
internal fun placeTable(rows: List<List<TableCell>>): TablePlacement {
    // For each column, the first row index that is free again.
    val freeFromRow = mutableMapOf<Int, Int>()
    val placed = mutableListOf<PlacedCell>()
    var columnCount = 0
    rows.forEachIndexed { rowIndex, row ->
        var column = 0
        row.forEach { cell ->
            while ((freeFromRow[column] ?: 0) > rowIndex) column++
            val colSpan = cell.colSpan.coerceAtLeast(1)
            val rowSpan = cell.rowSpan.coerceIn(1, rows.size - rowIndex)
            placed += PlacedCell(cell, rowIndex, column, rowSpan, colSpan)
            for (covered in column until column + colSpan) freeFromRow[covered] = rowIndex + rowSpan
            column += colSpan
        }
        columnCount = maxOf(columnCount, column)
    }
    // A rowspan cell can reach further right than any row's last cell.
    columnCount = maxOf(columnCount, placed.maxOfOrNull { it.column + it.colSpan } ?: 0)
    return TablePlacement(placed, rows.size, columnCount)
}

private val MinColumnWidth = 130.dp

/** Wide enough for a compact crafting grid, its arrow and its result. */
private val RecipeColumnWidth = 176.dp

/**
 * A table in a horizontally scrolling frame. Columns are at least [MinColumnWidth] wide, so a wide table
 * scrolls sideways and a narrow one fills the screen.
 */
@Composable
internal fun TableBlock(
    table: ContentBlock.Table,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    val placement = remember(table) { placeTable(table.rows) }
    Column(modifier = modifier.fillMaxWidth()) {
        table.caption?.takeUnless { it.isBlank }?.let { caption ->
            RichTextView(
                text = caption,
                onLinkClick = onLinkClick,
                modifier = Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (placement.columnCount > 0) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val hasRecipes = table.rows.any { row -> row.any { it.crafting != null } }
                val minWidth = if (hasRecipes) RecipeColumnWidth else MinColumnWidth
                val columnWidth = (maxWidth / placement.columnCount).coerceAtLeast(minWidth)
                TableGrid(
                    placement = placement,
                    columnWidth = columnWidth,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    placement.cells.forEach { placed ->
                        TableCellView(
                            cell = placed.cell,
                            onLinkClick = onLinkClick,
                            modifier = Modifier.gridPosition(placed),
                        )
                    }
                }
            }
        }
    }
}

private class GridPosition(
    val placed: PlacedCell,
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = placed
}

private fun Modifier.gridPosition(placed: PlacedCell): Modifier = this.then(GridPosition(placed))

/**
 * Lays [content] out on a grid of equal-width columns. A row is as tall as its tallest single-row cell; a cell
 * spanning rows that is taller than the rows it covers makes the last of them taller. Each cell is then
 * stretched to the full size of its squares, so backgrounds and borders line up.
 */
@Composable
private fun TableGrid(
    placement: TablePlacement,
    columnWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, _ ->
        val columnPx = columnWidth.roundToPx()
        val cells = measurables.map { it.parentData as PlacedCell }
        val naturalHeights =
            measurables.mapIndexed { index, measurable ->
                measurable.minIntrinsicHeight(columnPx * cells[index].colSpan)
            }

        val rowHeights = IntArray(placement.rowCount)
        cells.forEachIndexed { index, cell ->
            if (cell.rowSpan == 1) rowHeights[cell.row] = maxOf(rowHeights[cell.row], naturalHeights[index])
        }
        cells.forEachIndexed { index, cell ->
            if (cell.rowSpan > 1) {
                val covered = (cell.row until cell.row + cell.rowSpan).sumOf { rowHeights[it] }
                val last = cell.row + cell.rowSpan - 1
                rowHeights[last] += (naturalHeights[index] - covered).coerceAtLeast(0)
            }
        }
        val rowTops = IntArray(placement.rowCount + 1)
        rowHeights.forEachIndexed { row, height -> rowTops[row + 1] = rowTops[row] + height }

        val placeables =
            measurables.mapIndexed { index, measurable ->
                val cell = cells[index]
                val width = columnPx * cell.colSpan
                val height = rowTops[cell.row + cell.rowSpan] - rowTops[cell.row]
                measurable.measure(Constraints.fixed(width, height))
            }
        layout(columnPx * placement.columnCount, rowTops.last()) {
            placeables.forEachIndexed { index, placeable ->
                val cell = cells[index]
                placeable.place(cell.column * columnPx, rowTops[cell.row])
            }
        }
    }
}

@Composable
private fun TableCellView(
    cell: TableCell,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background =
        if (cell.isHeader) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    Column(
        modifier =
            modifier
                .background(background)
                .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant)
                .padding(8.dp),
    ) {
        val recipe = cell.crafting
        if (recipe != null) {
            CraftingGridBlock(grid = recipe, onLinkClick = onLinkClick, slotSize = CompactSlotSize)
        } else if (!cell.content.isBlank) {
            RichTextView(
                text = cell.content,
                onLinkClick = onLinkClick,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (cell.isHeader) FontWeight.Bold else null,
                    ),
            )
        }
    }
}
