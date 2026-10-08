package dev.cniekirk.wikidroid.core.articleparser

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.ContentBlock
import org.jsoup.Jsoup
import org.junit.Test

class MediaTest {
    @Test
    fun `thumbnail figures become images with captions`() {
        val html =
            """
            <figure class="mw-default-size mw-halign-right" typeof="mw:File/Thumb"><a href="/w/File:Crafting_Table_GUI.png" class="mw-file-description"><img src="/images/thumb/Crafting_Table_GUI.png/300px-Crafting_Table_GUI.png?28be1" decoding="async" loading="lazy" width="300" height="283" class="mw-file-element" srcset="/images/Crafting_Table_GUI.png?28be1 2x" data-file-width="352" data-file-height="332"/></a><figcaption>The <b>GUI</b> of the crafting table.</figcaption></figure>
            """.trimIndent()

        val image = blockOf<ContentBlock.Image>(html)

        assertThat(
            image.url,
        ).isEqualTo("https://minecraft.wiki/images/thumb/Crafting_Table_GUI.png/300px-Crafting_Table_GUI.png?28be1")
        assertThat(image.width).isEqualTo(300)
        assertThat(image.height).isEqualTo(283)
        assertThat(image.caption?.plainText).isEqualTo("The GUI of the crafting table.")
        assertThat(image.pixelated).isFalse()
    }

    @Test
    fun `sprites and pixel art are flagged as pixelated`() {
        val html =
            """
            <p><span class="pixel-image" typeof="mw:File"><a href="/w/File:Diamond.png">
            <img alt="Diamond.png" src="/images/Diamond_JE3_BE3.png?986a9" width="160" height="160"/></a></span></p>
            """.trimIndent()

        val image = blockOf<ContentBlock.Image>(html)

        assertThat(image.pixelated).isTrue()
        assertThat(image.altText).isEqualTo("Diamond.png")
        assertThat(image.caption).isNull()
    }

    @Test
    fun `protocol relative image urls get a scheme`() {
        val image =
            blockOf<ContentBlock.Image>("""<p><span typeof="mw:File"><img src="//cdn.example.com/a.png"/></span></p>""")

        assertThat(image.url).isEqualTo("https://cdn.example.com/a.png")
    }

    @Test
    fun `a paragraph of text with an inline image stays a paragraph`() {
        val blocks = blocksOf("<p>Text <span typeof=\"mw:File\"><img src=\"/images/a.png\"/></span> more</p>")

        assertThat(blocks.single()).isInstanceOf(ContentBlock.Paragraph::class.java)
    }

    @Test
    fun `galleries collect every thumbnail with its caption`() {
        val html =
            """
            <ul class="gallery mw-gallery-traditional">
              <li class="gallerybox" style="width: 155px"><div class="thumb"><span typeof="mw:File"><a href="/w/File:DiamondOre.png" class="mw-file-description"><img alt="Naturally occurring diamonds" src="/images/thumb/DiamondOre.png/120px-DiamondOre.png" width="120" height="64"/></a></span></div><div class="gallerytext">Naturally occurring diamonds</div></li>
              <li class="gallerybox"><div class="thumb"><span typeof="mw:File"><img src="/images/thumb/B.png/120px-B.png" width="120" height="64"/></span></div><div class="gallerytext"></div></li>
            </ul>
            """.trimIndent()

        val gallery = blockOf<ContentBlock.Gallery>(html)

        assertThat(gallery.images).hasSize(2)
        assertThat(gallery.images[0].caption?.plainText).isEqualTo("Naturally occurring diamonds")
        assertThat(gallery.images[1].caption).isNull()
    }

    @Test
    fun `a gallery with no images is dropped`() {
        assertThat(blocksOf("<ul class=\"gallery\"><li class=\"gallerybox\">Nothing</li></ul>")).isEmpty()
    }

    @Test
    fun `a standalone crafting widget becomes a 3x3 grid`() {
        val grid = blockOf<ContentBlock.CraftingGrid>("<div>${craftingWidget()}</div>")

        assertThat(grid.slots).hasSize(3)
        assertThat(grid.slots.map { it.size }).containsExactly(3, 3, 3)
        // The planks are in the middle row, first column; the first alternative of the animated slot wins.
        val planks = grid.slots[1][0]!!
        assertThat(planks.name).isEqualTo("Oak Planks")
        assertThat(planks.imageUrl).isEqualTo("https://minecraft.wiki/images/Invicon_Oak_Planks.png?53f69")
        assertThat(planks.link?.title).isEqualTo("Oak Planks")
        assertThat(grid.slots.flatten().count { it != null }).isEqualTo(4)
        assertThat(grid.output?.name).isEqualTo("Crafting Table")
        assertThat(grid.output?.count).isEqualTo(1)
    }

    @Test
    fun `stack sizes become slot counts`() {
        val widget =
            """
            <span class="mcui mcui-Crafting_Table pixel-image"><span class="mcui-input"><span class="mcui-row"><span class="invslot"><span class="invslot-item invslot-item-image"><span typeof="mw:File"><a href="/w/Diamond" title="Diamond"><img alt="Invicon Diamond.png" src="/images/Invicon_Diamond.png" width="32" height="32"/></a></span><span class="invslot-stacksize" title="Diamond">9</span></span></span></span></span><span class="mcui-arrow"><br/></span><span class="mcui-output"><span class="invslot invslot-large"><span class="invslot-item invslot-item-image"><span typeof="mw:File"><span title="Block of Diamond"><img alt="Invicon Block of Diamond.png" src="/images/Invicon_Block_of_Diamond.png"/></span></span><span class="invslot-stacksize">2</span></span></span></span></span>
            """.trimIndent()

        val grid = blockOf<ContentBlock.CraftingGrid>("<div>$widget</div>")

        assertThat(grid.slots[0][0]?.count).isEqualTo(9)
        assertThat(grid.slots[0][1]).isNull()
        assertThat(grid.slots[1]).containsExactly(null, null, null)
        assertThat(grid.output?.name).isEqualTo("Block of Diamond")
        assertThat(grid.output?.count).isEqualTo(2)
    }

    @Test
    fun `other inventory widgets are unsupported`() {
        val html = """<div><span class="mcui mcui-Furnace pixel-image"><span class="mcui-input"></span></span></div>"""

        val block = blockOf<ContentBlock.Unsupported>(html)

        assertThat(block.htmlSnippet).contains("mcui-Furnace")
    }

    @Test
    fun `a widget inside a table cell is summarised as text`() {
        val html = "<table class=\"wikitable\"><tr><th>Recipe</th></tr><tr><td>${craftingWidget()}</td></tr></table>"

        val table = blockOf<ContentBlock.Table>(html)

        assertThat(
            table.rows[1]
                .single()
                .content.plainText,
        ).isEqualTo("Oak Planks → Crafting Table")
    }

    @Test
    fun `a cell holding only a recipe also carries it as a grid`() {
        val html = "<table class=\"wikitable\"><tr><th>Recipe</th></tr><tr><td>${craftingWidget()}</td></tr></table>"

        val cell = blockOf<ContentBlock.Table>(html).rows[1].single()

        assertThat(cell.crafting?.output?.name).isEqualTo("Crafting Table")
        assertThat(
            cell.crafting?.slots?.flatten()?.mapNotNull {
                it?.name
            },
        ).containsExactly("Oak Planks", "Oak Planks", "Oak Planks", "Oak Planks")
    }

    @Test
    fun `a cell with other text next to a recipe keeps only the summary`() {
        val html =
            "<table class=\"wikitable\"><tr><td>Shaped: ${craftingWidget()}</td></tr></table>"

        val cell = blockOf<ContentBlock.Table>(html).rows[0].single()

        assertThat(cell.crafting).isNull()
        assertThat(cell.content.plainText).contains("Oak Planks → Crafting Table")
    }

    // The first crafting recipe of the Crafting Table article, exactly as the wiki serves it.
    private fun craftingWidget(): String =
        Jsoup.parse(fixture("crafting_table")).selectFirst(".mcui-Crafting_Table")!!.outerHtml()
}
