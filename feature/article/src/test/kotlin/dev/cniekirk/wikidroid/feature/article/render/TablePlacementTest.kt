package dev.cniekirk.wikidroid.feature.article.render

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.RichText
import dev.cniekirk.wikidroid.core.model.TableCell
import org.junit.Test

class TablePlacementTest {
    private fun cell(
        label: String,
        colSpan: Int = 1,
        rowSpan: Int = 1,
    ) = TableCell(RichText.of(label), colSpan = colSpan, rowSpan = rowSpan)

    private fun TablePlacement.at(label: String) = cells.single { it.cell.content.plainText == label }

    @Test
    fun aPlainGridPlacesCellsLeftToRight() {
        val placement = placeTable(listOf(listOf(cell("a"), cell("b")), listOf(cell("c"), cell("d"))))

        assertThat(placement.rowCount).isEqualTo(2)
        assertThat(placement.columnCount).isEqualTo(2)
        assertThat(placement.at("d").row).isEqualTo(1)
        assertThat(placement.at("d").column).isEqualTo(1)
    }

    @Test
    fun aColspanCellPushesTheNextOneRight() {
        val placement =
            placeTable(listOf(listOf(cell("wide", colSpan = 2), cell("c")), listOf(cell("a"), cell("b"), cell("c2"))))

        assertThat(placement.at("c").column).isEqualTo(2)
        assertThat(placement.at("wide").colSpan).isEqualTo(2)
        assertThat(placement.columnCount).isEqualTo(3)
    }

    @Test
    fun aRowspanCellKeepsLaterRowsOutOfItsColumn() {
        val placement =
            placeTable(
                listOf(
                    listOf(cell("tall", rowSpan = 2), cell("a")),
                    listOf(cell("b")),
                    listOf(cell("c"), cell("d")),
                ),
            )

        assertThat(placement.at("b").column).isEqualTo(1)
        // The rowspan ended with row 1, so row 2 starts in column 0 again.
        assertThat(placement.at("c").column).isEqualTo(0)
        assertThat(placement.at("d").column).isEqualTo(1)
    }

    @Test
    fun aRowspanLongerThanTheTableIsClamped() {
        val placement = placeTable(listOf(listOf(cell("tall", rowSpan = 9)), listOf(cell("x"))))

        assertThat(placement.at("tall").rowSpan).isEqualTo(2)
    }

    @Test
    fun aRowspanAndColspanTogetherCoverABlock() {
        val placement =
            placeTable(
                listOf(
                    listOf(cell("block", colSpan = 2, rowSpan = 2), cell("a")),
                    listOf(cell("b")),
                ),
            )

        assertThat(placement.at("a").column).isEqualTo(2)
        assertThat(placement.at("b").column).isEqualTo(2)
        assertThat(placement.columnCount).isEqualTo(3)
    }

    @Test
    fun anEmptyTableHasNoColumns() {
        val placement = placeTable(emptyList())

        assertThat(placement.cells).isEmpty()
        assertThat(placement.columnCount).isEqualTo(0)
    }
}
