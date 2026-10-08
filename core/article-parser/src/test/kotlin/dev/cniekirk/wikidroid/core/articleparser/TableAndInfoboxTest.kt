package dev.cniekirk.wikidroid.core.articleparser

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.Link
import org.junit.Test

class TableAndInfoboxTest {
    @Test
    fun `tables keep header cells, spans and the caption`() {
        val html =
            """
            <table class="wikitable sortable">
            <caption>Loot</caption>
            <tbody><tr><th>Item</th><th>Chance</th></tr>
            <tr><th colspan="2"><i><a href="/w/Java_Edition">Java Edition</a></i></th></tr>
            <tr><td rowspan="2"><b>Diamond</b></td><td>36%</td></tr>
            <tr><td>12%</td></tr></tbody>
            <tfoot><tr><td colspan="2">Total</td></tr></tfoot>
            </table>
            """.trimIndent()

        val table = blockOf<ContentBlock.Table>(html)

        assertThat(table.caption?.plainText).isEqualTo("Loot")
        assertThat(table.rows.map { row -> row.map { it.content.plainText } })
            .containsExactly(
                listOf("Item", "Chance"),
                listOf("Java Edition"),
                listOf("Diamond", "36%"),
                listOf("12%"),
                listOf("Total"),
            ).inOrder()
        assertThat(table.rows[0].all { it.isHeader }).isTrue()
        assertThat(table.rows[1].single().colSpan).isEqualTo(2)
        assertThat(
            table.rows[1]
                .single()
                .content.links,
        ).containsExactly(Link.Internal("Java Edition"))
        assertThat(table.rows[2][0].rowSpan).isEqualTo(2)
        assertThat(table.rows[2][1].isHeader).isFalse()
    }

    @Test
    fun `a table nested in a cell is flattened into the cell text`() {
        val html = "<table><tr><td>Outer<table><tr><td>Inner A</td><td>Inner B</td></tr></table></td></tr></table>"

        val table = blockOf<ContentBlock.Table>(html)

        assertThat(table.rows).hasSize(1)
        assertThat(
            table.rows
                .single()
                .single()
                .content.plainText,
        ).isEqualTo("Outer\nInner A Inner B")
    }

    @Test
    fun `cell lists keep a bullet per item and line breaks stay`() {
        val html = "<table><tr><td><ul><li>One</li><li>Two</li></ul></td><td>a<br/>b</td></tr></table>"

        val cells = blockOf<ContentBlock.Table>(html).rows.single()

        assertThat(cells[0].content.plainText).isEqualTo("• One\n• Two")
        assertThat(cells[1].content.plainText).isEqualTo("a\nb")
    }

    @Test
    fun `an empty table is dropped`() {
        assertThat(blocksOf("<table><tbody></tbody></table>")).isEmpty()
    }

    @Test
    fun `infobox gets a title, images, rows and section rows`() {
        val html =
            """
            <div class="infobox notaninfobox">
            <div class="mcwiki-header infobox-title">Creeper</div>
            <div class="infobox-imagearea animated-container"><div><div class="tabber">
            <div class="tabbertab" data-title="Normal"><div><span typeof="mw:File"><a href="/w/File:Creeper.png"><img alt="Creeper.png" src="/images/thumb/Creeper.png/128px-Creeper.png" width="128" height="261"/></a></span></div></div>
            <div class="tabbertab" data-title="Charged"><div><span class="pixel-image" typeof="mw:File"><img alt="Charged" src="/images/Charged.webp" width="150" height="314"/></span></div></div>
            </div></div>
            <div class="infobox-invimages"><div><span class="invslot"><span class="invslot-item"><span typeof="mw:File"><span title="Creeper Spawn Egg"><img alt="Invicon" src="/images/Invicon_Creeper_Spawn_Egg.png" width="32" height="32"/></span></span></span></span></div></div></div>
            <table class="infobox-rows" cellspacing="1" cellpadding="4"><tbody>
            <tr><th><a href="/w/Health" title="Health">Health points</a></th><td>20<span class="hidden-alt-text">HP</span> × 10</td></tr>
            <tr><th colspan="2">Java Edition</th></tr>
            <tr><td colspan="2"><i>Spawns at night.</i></td></tr>
            <tr><th>Empty</th><td> </td></tr>
            </tbody></table>
            </div>
            """.trimIndent()

        val infobox = blockOf<ContentBlock.Infobox>(html)

        assertThat(infobox.title).isEqualTo("Creeper")
        assertThat(infobox.images.map { it.caption?.plainText }).containsExactly("Normal", "Charged").inOrder()
        assertThat(infobox.images.map { it.pixelated }).containsExactly(false, true).inOrder()
        assertThat(
            infobox.images.map { it.url },
        ).doesNotContain("https://minecraft.wiki/images/Invicon_Creeper_Spawn_Egg.png")
        assertThat(infobox.rows).hasSize(3)
        assertThat(infobox.rows[0].label?.plainText).isEqualTo("Health points")
        assertThat(infobox.rows[0].label?.links).containsExactly(Link.Internal("Health"))
        assertThat(infobox.rows[0].value.plainText).isEqualTo("20 × 10")
        assertThat(infobox.rows[1].label).isNull()
        assertThat(infobox.rows[1].value.plainText).isEqualTo("Java Edition")
        assertThat(infobox.rows[2].label).isNull()
        assertThat(infobox.rows[2].value.plainText).isEqualTo("Spawns at night.")
    }

    @Test
    fun `an infobox with nothing in it is dropped`() {
        assertThat(blocksOf("<div class=\"infobox\"></div>")).isEmpty()
    }
}
