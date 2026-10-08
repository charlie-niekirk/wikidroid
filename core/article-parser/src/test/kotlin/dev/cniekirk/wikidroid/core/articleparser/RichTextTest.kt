package dev.cniekirk.wikidroid.core.articleparser

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.model.RichText
import dev.cniekirk.wikidroid.core.model.TextStyleFlag
import org.junit.Test

class RichTextTest {
    private fun rich(html: String): RichText = blockOf<ContentBlock.Paragraph>("<p>$html</p>").text

    @Test
    fun `applies inline styles and merges neighbouring spans`() {
        val text =
            rich(
                "A <b>bold</b> and <i><b>both</b></i>, <code>x</code> " +
                    "H<sub>2</sub>O m<sup>2</sup> <s>old</s> <u>new</u>",
            )

        assertThat(text.plainText).isEqualTo("A bold and both, x H2O m2 old new")
        assertThat(text.spans.first { it.text == "bold" }.styles).containsExactly(TextStyleFlag.Bold)
        assertThat(
            text.spans.first { it.text == "both" }.styles,
        ).containsExactly(TextStyleFlag.Bold, TextStyleFlag.Italic)
        assertThat(text.spans.first { it.text == "x" }.styles).containsExactly(TextStyleFlag.Code)
        assertThat(text.spans.first { it.text == "2" && TextStyleFlag.Subscript in it.styles }).isNotNull()
        assertThat(text.spans.first { it.text == "2" && TextStyleFlag.Superscript in it.styles }).isNotNull()
        assertThat(text.spans.first { it.text == "old" }.styles).containsExactly(TextStyleFlag.Strikethrough)
        assertThat(text.spans.first { it.text == "new" }.styles).containsExactly(TextStyleFlag.Underline)
    }

    @Test
    fun `turns br into a newline`() {
        assertThat(rich("one<br/>two<br><br><br>three").plainText).isEqualTo("one\ntwo\n\nthree")
        assertThat(rich("<br/>lead and trail<br/>").plainText).isEqualTo("lead and trail")
    }

    @Test
    fun `resolves an internal link with its anchor`() {
        val text = rich("""See <a href="/w/Renewable_resource#Vault" title="Renewable resource">renewables</a>.""")

        assertThat(text.links).containsExactly(Link.Internal("Renewable resource", "Vault"))
        assertThat(text.spans.first { it.link != null }.text).isEqualTo("renewables")
    }

    @Test
    fun `decodes percent escapes in titles and keeps plus signs`() {
        val text = rich("""<a href="/w/C%2B%2B_(language)">a</a><a href="/w/Fish_%26_Chips">b</a>""")

        assertThat(text.links).containsExactly(Link.Internal("C++ (language)"), Link.Internal("Fish & Chips")).inOrder()
    }

    @Test
    fun `absolute links to the wiki are internal and other hosts are external`() {
        val text =
            rich(
                """
                <a href="https://minecraft.wiki/w/Creeper">x</a>
                <a href="https://example.com/a?b=c">y</a>
                <a href="//example.org/z">z</a>
                """.trimIndent(),
            )

        assertThat(text.links)
            .containsExactly(
                Link.Internal("Creeper"),
                Link.External("https://example.com/a?b=c"),
                Link.External("https://example.org/z"),
            ).inOrder()
    }

    @Test
    fun `file and special pages open in the browser`() {
        val text = rich("""<a href="/w/File:Diamond.png">f</a><a href="/w/Special:Search">s</a>""")

        assertThat(text.links)
            .containsExactly(
                Link.External("https://minecraft.wiki/w/File:Diamond.png"),
                Link.External("https://minecraft.wiki/w/Special:Search"),
            ).inOrder()
    }

    @Test
    fun `red links and same page fragments are plain text`() {
        val text =
            rich(
                """
                <a href="/index.php?title=Nope&amp;action=edit&amp;redlink=1" class="new">missing</a>
                <sup><a href="#cite_note-1">[1]</a></sup>
                <a class="mw-selflink selflink">self</a>
                """.trimIndent(),
            )

        assertThat(text.links).isEmpty()
        assertThat(text.plainText).isEqualTo("missing [1] self")
    }

    @Test
    fun `styles carry through a link`() {
        val span = rich("""<a href="/w/Arrow"><i>arrows</i></a>""").spans.single()

        assertThat(span.styles).containsExactly(TextStyleFlag.Italic)
        assertThat(span.link).isEqualTo(Link.Internal("Arrow"))
    }

    @Test
    fun `an inline sprite becomes an image span before its label`() {
        val text =
            rich(
                """
                <span class="nowrap"><span class="sprite-file"><span class="pixel-image" typeof="mw:File">
                <img alt="" src="/images/ItemSprite_diamond.png" width="16" height="16"/></span></span><a href="/w/Diamond"><span class="sprite-text">Diamond</span></a></span>
                """.trimIndent(),
            )

        val image = text.spans.first().image
        assertThat(image).isNotNull()
        assertThat(image?.url).isEqualTo("https://minecraft.wiki/images/ItemSprite_diamond.png")
        assertThat(image?.width).isEqualTo(16)
        assertThat(image?.height).isEqualTo(16)
        assertThat(image?.pixelated).isTrue()
        // The label and its link are unchanged, and the image adds nothing to the plain text.
        assertThat(text.plainText).isEqualTo("Diamond")
        assertThat(text.links).containsExactly(Link.Internal("Diamond"))
    }

    @Test
    fun `a sprite inside a link carries that link`() {
        val text = rich("""<a href="/w/Diamond"><img alt="" src="/images/D.png" width="16" height="16"/> Diamond</a>""")

        assertThat(text.spans.first().image).isNotNull()
        assertThat(text.spans.first().link).isEqualTo(Link.Internal("Diamond"))
    }

    @Test
    fun `the space after an inline image is kept`() {
        val text = rich("""<img alt="" src="/images/D.png"/> Diamond and <img alt="" src="/images/E.png"/> Emerald""")

        assertThat(text.plainText).isEqualTo(" Diamond and  Emerald")
        assertThat(text.spans.count { it.image != null }).isEqualTo(2)
    }

    @Test
    fun `an image with no source is ignored`() {
        val text = rich("""Before <img alt="x"/> after""")

        assertThat(text.spans.none { it.image != null }).isTrue()
        assertThat(text.plainText).isEqualTo("Before after")
    }

    @Test
    fun `an icon alone is not blank but is not text either`() {
        val text = rich("""<span class="sprite-file"><img alt="" src="/images/D.png" width="16" height="16"/></span>""")

        assertThat(text.isBlank).isFalse()
        assertThat(text.hasText).isFalse()
    }

    @Test
    fun `the decoration on a hatnote is left out`() {
        val note =
            blockOf<ContentBlock.Note>(
                """
                <div role="note" class="hatnote"><span typeof="mw:File"><span>
                <img alt="" src="/images/Disambig.svg" width="12" height="9"/></span></span> For other uses, see X.</div>
                """.trimIndent(),
            )

        assertThat(note.text.spans.none { it.image != null }).isTrue()
        assertThat(note.text.plainText).isEqualTo("For other uses, see X.")
    }

    @Test
    fun `a picture inside a message box is kept`() {
        val note =
            blockOf<ContentBlock.Note>(
                """
                <div class="msgbox"><div><span class="pixel-image" typeof="mw:File"><span>
                <img alt="" src="/images/thumb/Book.png/24px-Book.png" width="24" height="24"/></span></span> A tutorial exists.</div></div>
                """.trimIndent(),
            )

        assertThat(
            note.text.spans
                .first()
                .image
                ?.width,
        ).isEqualTo(24)
        assertThat(note.text.plainText).isEqualTo(" A tutorial exists.")
    }

    @Test
    fun `a paragraph of only inline sprites stays a paragraph`() {
        val html =
            """<p><span class="sprite-file"><img alt="" src="/images/D.png" width="16" height="16"/></span></p>"""

        val paragraph = blockOf<ContentBlock.Paragraph>(html)

        assertThat(
            paragraph.text.spans
                .single()
                .image,
        ).isNotNull()
    }

    @Test
    fun `a table cell with an icon and a label shows both`() {
        val html =
            """<table><tr><td><span class="sprite-file"><img alt="" src="/images/D.png" width="16" height="16"/>""" +
                """</span><span class="sprite-text">Diamond</span></td></tr></table>"""

        val cell = blockOf<ContentBlock.Table>(html).rows.single().single()

        assertThat(
            cell.content.spans
                .first()
                .image,
        ).isNotNull()
        assertThat(cell.content.plainText).isEqualTo("Diamond")
        assertThat(cell.crafting).isNull()
    }

    @Test
    fun `icon bars keep the emoji alt text`() {
        val text =
            rich(
                """
                20 <span class="iconbar pixel-image"><span typeof="mw:File"><span>
                <img alt="❤️" src="/images/Heart.png"/></span></span></span> × 10
                """.trimIndent(),
            )

        assertThat(text.plainText).isEqualTo("20 ❤️ × 10")
    }

    @Test
    fun `bare text between blocks becomes a paragraph`() {
        val blocks = blocksOf("<div>Loose <b>text</b><p>Para</p>tail</div>")

        assertThat(
            blocks.map {
                (it as ContentBlock.Paragraph).text.plainText
            },
        ).containsExactly("Loose text", "Para", "tail").inOrder()
    }
}
