package dev.cniekirk.wikidroid.core.articleparser

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.model.TextStyleFlag
import org.junit.Test

class StructureTest {
    @Test
    fun `h2 starts a section and deeper headings stay in the blocks`() {
        val html =
            """
            <div class="mw-parser-output">
            <section class="mf-section-0" id="mf-section-0"><p>Lead</p></section>
            <div class="mw-heading mw-heading2 section-heading" onclick="mfTempOpenSection(1)"><span class="indicator mf-icon"></span><h2 id="Obtaining">Obtaining</h2></div>
            <section class="mf-section-1 collapsible-block" id="mf-section-1">
            <div class="mw-heading mw-heading3"><h3 id="Mining">Mining</h3></div>
            <p>Dig.</p>
            <div class="mw-heading mw-heading4"><h4 id="Deep">Deep <span class="mw-editsection">[edit]</span></h4></div>
            <p>Deeper.</p>
            </section>
            </div>
            """.trimIndent()

        val sections = parse(html)

        assertThat(sections).hasSize(2)
        assertThat(sections[0].heading).isNull()
        assertThat(
            sections[1].heading,
        ).isEqualTo(ContentBlock.Heading(level = 2, text = "Obtaining", anchor = "Obtaining"))
        assertThat(sections[1].blocks.filterIsInstance<ContentBlock.Heading>())
            .containsExactly(ContentBlock.Heading(3, "Mining", "Mining"), ContentBlock.Heading(4, "Deep", "Deep"))
            .inOrder()
    }

    @Test
    fun `works on markup without mobile section wrappers`() {
        val html =
            """
            <p>Lead</p>
            <div class="mw-heading mw-heading2"><h2 id="A">A</h2></div><p>One</p>
            <h2 id="B">B</h2><p>Two</p>
            """.trimIndent()

        val sections = parse(html)

        assertThat(sections.map { it.heading?.anchor }).containsExactly(null, "A", "B").inOrder()
    }

    @Test
    fun `a section with only subsections is kept`() {
        val sections = parse("<h2 id=\"A\">A</h2><h2 id=\"B\">B</h2><p>x</p>")

        assertThat(sections.map { it.heading?.text }).containsExactly("A", "B").inOrder()
        assertThat(sections.first().blocks).isEmpty()
    }

    @Test
    fun `hatnotes and message boxes become notes`() {
        val html =
            """
            <div role="note" class="hatnote searchaux"><span typeof="mw:File"><span><img alt="" src="/images/Disambig_color.svg" width="12" height="9"/></span></span> For other uses, see <a href="/w/Diamond_(disambiguation)">Diamond (disambiguation)</a>.</div>
            <div class="msgbox searchaux"><div class="msgbox-icon"> </div><div><div><b>Related tutorial!</b></div><div class="msgbox-text">See <a href="/w/Tutorial:Mining">Mining</a>.</div></div></div>
            """.trimIndent()

        val notes = blocksOf(html).filterIsInstance<ContentBlock.Note>()

        assertThat(notes).hasSize(2)
        assertThat(notes[0].text.plainText).isEqualTo("For other uses, see Diamond (disambiguation).")
        assertThat(notes[0].text.links).containsExactly(Link.Internal("Diamond (disambiguation)"))
        assertThat(notes[1].text.plainText).isEqualTo("Related tutorial!\nSee Mining.")
    }

    @Test
    fun `unordered, ordered and nested lists`() {
        val html =
            """
            <ul><li>One <a href="/w/Iron">iron</a></li><li>Two<ul><li>Two A</li><li><b>Two B</b></li></ul></li></ul>
            <ol><li>First</li><li>Second</li></ol>
            """.trimIndent()

        val (bullets, numbers) = blocksOf(html).filterIsInstance<ContentBlock.ListBlock>()

        assertThat(bullets.ordered).isFalse()
        assertThat(bullets.items.map { it.text.plainText }).containsExactly("One iron", "Two").inOrder()
        assertThat(bullets.items[0].text.links).containsExactly(Link.Internal("Iron"))
        val nested = bullets.items[1].sublists.single()
        assertThat(nested.items.map { it.text.plainText }).containsExactly("Two A", "Two B").inOrder()
        assertThat(
            nested.items[1]
                .text.spans
                .single()
                .styles,
        ).containsExactly(TextStyleFlag.Bold)
        assertThat(numbers.ordered).isTrue()
        assertThat(numbers.items.map { it.text.plainText }).containsExactly("First", "Second").inOrder()
    }

    @Test
    fun `a list nested directly in a list attaches to the previous item`() {
        val list = blockOf<ContentBlock.ListBlock>("<ul><li>Parent</li><ul><li>Child</li></ul></ul>")

        assertThat(list.items).hasSize(1)
        assertThat(
            list.items
                .single()
                .sublists
                .single()
                .items
                .single()
                .text.plainText,
        ).isEqualTo("Child")
    }

    @Test
    fun `empty list items are dropped`() {
        val list = blockOf<ContentBlock.ListBlock>("<ul><li> </li><li>Kept</li></ul>")

        assertThat(list.items.map { it.text.plainText }).containsExactly("Kept")
    }

    @Test
    fun `definition lists give a bold term and a paragraph per definition`() {
        val blocks =
            blocksOf(
                "<dl><dt>Trim color palette</dt><dd>Shown on armor.</dd></dl>",
            ).filterIsInstance<ContentBlock.Paragraph>()

        assertThat(blocks.map { it.text.plainText }).containsExactly("Trim color palette", "Shown on armor.").inOrder()
        assertThat(
            blocks[0]
                .text.spans
                .single()
                .styles,
        ).containsExactly(TextStyleFlag.Bold)
    }

    @Test
    fun `preformatted text keeps its layout as code`() {
        val block = blockOf<ContentBlock.Paragraph>("<pre>/give @p diamond\n  64</pre>")

        assertThat(
            block.text.spans
                .single()
                .text,
        ).isEqualTo("/give @p diamond\n  64")
        assertThat(
            block.text.spans
                .single()
                .styles,
        ).containsExactly(TextStyleFlag.Code)
    }

    @Test
    fun `tabs keep their title and their content`() {
        val html =
            """
            <div class="tabber">
            <div class="tabbertab" data-title="Java Edition"><p>Java text</p></div>
            <div class="tabbertab" data-title="Bedrock Edition"><p>Bedrock text</p></div>
            </div>
            """.trimIndent()

        val text = blocksOf(html).map { (it as ContentBlock.Paragraph).text.plainText }

        assertThat(text).containsExactly("Java Edition", "Java text", "Bedrock Edition", "Bedrock text").inOrder()
    }

    @Test
    fun `widgets that need the site become unsupported`() {
        val html =
            """
            <p>Before</p>
            <figure class="embedvideo" data-service="youtube"><div class="embedvideo-wrapper"><iframe src="https://www.youtube-nocookie.com/embed/x"></iframe></div></figure>
            <div class="issue-list" data-mc="project = MC"></div>
            <div class="mcw-calc" data-type="blockDistribution"></div>
            <table class="wikitable calculator-container"><tr><td>calc</td></tr></table>
            <iframe src="https://example.com"></iframe>
            <p>After</p>
            """.trimIndent()

        val blocks = blocksOf(html)

        assertThat(blocks.map { it::class })
            .containsExactly(
                ContentBlock.Paragraph::class,
                ContentBlock.Unsupported::class,
                ContentBlock.Unsupported::class,
                ContentBlock.Unsupported::class,
                ContentBlock.Unsupported::class,
                ContentBlock.Unsupported::class,
                ContentBlock.Paragraph::class,
            ).inOrder()
        assertThat((blocks[1] as ContentBlock.Unsupported).htmlSnippet).contains("embedvideo")
    }

    @Test
    fun `unsupported snippets are capped`() {
        val block =
            blockOf<ContentBlock.Unsupported>("""<div class="issue-list" data-mc="${"x".repeat(5_000)}"></div>""")

        assertThat(block.htmlSnippet.length).isAtMost(1_000)
    }

    @Test
    fun `unknown containers are looked through`() {
        val html =
            """
            <div class="collapsible"><div>Creeper spawns in:</div>
            <div class="collapsible-content"><table class="wikitable"><tr><th>A</th></tr></table></div></div>
            """.trimIndent()

        val blocks = blocksOf(html)

        assertThat(
            blocks.map { it::class },
        ).containsExactly(ContentBlock.Paragraph::class, ContentBlock.Table::class).inOrder()
    }
}
