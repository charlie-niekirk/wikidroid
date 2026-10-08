package dev.cniekirk.wikidroid.core.articleparser

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.RichText
import org.junit.Test

class StrippingTest {
    private fun text(html: String): List<String> =
        blocksOf(html).filterIsInstance<ContentBlock.Paragraph>().map { it.text.plainText }

    @Test
    fun `drops everything from the Navigation heading onward`() {
        val html =
            """
            <div class="mw-parser-output">
            <section class="mf-section-0"><p>Lead.</p></section>
            <div class="mw-heading mw-heading2 section-heading"><h2 id="Trivia">Trivia</h2></div>
            <section class="mf-section-1"><p>Fun fact.</p></section>
            <div class="mw-heading mw-heading2 section-heading"><h2 id="Navigation">Navigation</h2></div>
            <section class="mf-section-2"><p>Footer text.</p></section>
            <p>Trailing.</p>
            </div>
            """.trimIndent()

        val sections = parse(html)

        assertThat(sections.map { it.heading?.text }).containsExactly(null, "Trivia").inOrder()
        assertThat(sections.allBlocks().filterIsInstance<ContentBlock.Paragraph>().map { it.text.plainText })
            .containsExactly("Lead.", "Fun fact.")
    }

    @Test
    fun `drops navigation when the heading is not wrapped`() {
        val html = "<p>Body.</p><h2 id=\"Navigation\">Navigation</h2><p>Footer.</p>"

        assertThat(text(html)).containsExactly("Body.")
    }

    @Test
    fun `strips navboxes and anything marked not searchable`() {
        val html =
            """
            <p>Kept.</p>
            <table class="navbox hlist navigation-not-searchable"><tbody><tr><td>Nav</td></tr></tbody></table>
            <div class="navigation-not-searchable"><p>Also nav.</p></div>
            """.trimIndent()

        assertThat(blocksOf(html)).hasSize(1)
        assertThat(text(html)).containsExactly("Kept.")
    }

    @Test
    fun `strips embedded JSON data blocks`() {
        val html =
            """
            <p>Text</p>
            <pre class="history-json noexcerpt navigation-not-searchable">{ "title": "Diamond" }</pre>
            <table class="wikitable"><tr><td>36%<pre class="chest-json noexcerpt">{ "item": "Diamond" }</pre></td></tr></table>
            """.trimIndent()

        val table = blocksOf(html).filterIsInstance<ContentBlock.Table>().single()

        assertThat(blocksOf(html).filterIsInstance<ContentBlock.Paragraph>()).hasSize(1)
        assertThat(
            table.rows
                .single()
                .single()
                .content.plainText,
        ).isEqualTo("36%")
    }

    @Test
    fun `strips edit links, the table of contents, mini navbars and styles`() {
        val html =
            """
            <style>.x { color: red }</style>
            <div id="toc"><p>Contents</p></div>
            <div class="navbar-mini"><p>v t e</p></div>
            <p>Hello<span class="mw-editsection"><a href="/edit">edit</a></span> world</p>
            """.trimIndent()

        assertThat(text(html)).containsExactly("Hello world")
    }

    @Test
    fun `strips hidden accessibility text`() {
        val html = "<p>20<span class=\"hidden-alt-text\">HP</span></p>"

        assertThat(text(html)).containsExactly("20")
    }

    @Test
    fun `an empty document gives no sections`() {
        assertThat(parse("")).isEmpty()
        assertThat(parse("<div class=\"mw-parser-output\"><p class=\"mw-empty-elt\"></p></div>")).isEmpty()
    }

    @Test
    fun `plain text is trimmed and collapsed`() {
        val html = "<p>\n  Some \n  spaced\t text  \n</p>"

        assertThat(blocksOf(html).single()).isEqualTo(ContentBlock.Paragraph(RichText.of("Some spaced text")))
    }
}
