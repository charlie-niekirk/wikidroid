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
    fun `inline sprites are dropped but their label is kept`() {
        val text =
            rich(
                """
                <span class="nowrap"><span class="sprite-file"><span class="pixel-image" typeof="mw:File">
                <img alt="" src="/images/ItemSprite_diamond.png" width="16" height="16"/></span></span><a href="/w/Diamond"><span class="sprite-text">Diamond</span></a></span>
                """.trimIndent(),
            )

        assertThat(text.plainText).isEqualTo("Diamond")
        assertThat(text.links).containsExactly(Link.Internal("Diamond"))
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
