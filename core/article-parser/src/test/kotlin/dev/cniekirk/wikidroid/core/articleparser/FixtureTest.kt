package dev.cniekirk.wikidroid.core.articleparser

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.ArticleSection
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.Link
import org.junit.Test
import kotlin.time.measureTime

/** Parses whole pages exactly as `action=parse&mobileformat=1` returned them. */
class FixtureTest {
    private fun List<ArticleSection>.everyText(): String = allBlocks().joinToString("\n") { it.toString() }

    @Test
    fun `Diamond has the expected sections`() {
        val sections = parse(fixture("diamond"))

        assertThat(sections.map { it.heading?.text })
            .containsExactly(
                null,
                "Obtaining",
                "Usage",
                "Data values",
                "Achievements",
                "Advancements",
                "Videos",
                "History",
                "Issues",
                "Trivia",
                "Gallery",
                "External links",
            ).inOrder()
    }

    @Test
    fun `Diamond opens with a hatnote, the lead paragraph and the infobox`() {
        val lead = parse(fixture("diamond")).first().blocks

        val note = lead[0] as ContentBlock.Note
        assertThat(note.text.links).containsExactly(Link.Internal("Diamond (disambiguation)"))
        val paragraph = lead[1] as ContentBlock.Paragraph
        assertThat(paragraph.text.plainText).startsWith("A diamond is a mineral")
        val infobox = lead.filterIsInstance<ContentBlock.Infobox>().single()
        assertThat(infobox.title).isEqualTo("Diamond")
        assertThat(infobox.images.single().pixelated).isTrue()
        assertThat(infobox.rows.map { it.label?.plainText }).contains("Stackable")
        assertThat(
            infobox.rows
                .first { it.label?.plainText == "Stackable" }
                .value.plainText,
        ).isEqualTo("Yes (64)")
    }

    @Test
    fun `Diamond drops navigation and embedded data`() {
        val text = parse(fixture("diamond")).everyText()

        assertThat(text).doesNotContain("navbox")
        assertThat(text).doesNotContain("\"stacksize\"")
        assertThat(text).doesNotContain("\"rows\"")
        assertThat(text).doesNotContain("Navigation")
        assertThat(text).doesNotContain("mf-icon")
    }

    @Test
    fun `Diamond loot table and recipes parse`() {
        val obtaining = parse(fixture("diamond")).section("Obtaining")
        val loot = obtaining.blocks.filterIsInstance<ContentBlock.Table>().first()
        assertThat(
            loot.rows.first().map {
                it.content.plainText
            },
        ).containsExactly("Item", "Structure", "Container", "Quantity", "Chance").inOrder()
        assertThat(loot.rows.flatten().map { it.content.plainText }).contains("Abandoned Camp")

        val recipes =
            parse(fixture("diamond"))
                .allBlocks()
                .filterIsInstance<ContentBlock.Table>()
                .flatMap {
                    it.rows.flatten()
                }.map { it.content.plainText }
        assertThat(recipes).containsAtLeast("Diamond → Block of Diamond", "Block of Diamond → Diamond ×9")
    }

    @Test
    fun `Diamond gallery parses`() {
        val gallery =
            parse(
                fixture("diamond"),
            ).section("Gallery").blocks.filterIsInstance<ContentBlock.Gallery>().first()

        assertThat(gallery.images).isNotEmpty()
        assertThat(gallery.images.all { it.url.startsWith("https://minecraft.wiki/images/") }).isTrue()
    }

    @Test
    fun `Creeper infobox has both variants and health bar text`() {
        val infobox =
            parse(fixture("creeper"))
                .first()
                .blocks
                .filterIsInstance<ContentBlock.Infobox>()
                .single()

        assertThat(infobox.title).isEqualTo("Creeper")
        assertThat(infobox.images.map { it.caption?.plainText }).containsExactly("Normal", "Charged").inOrder()
        assertThat(
            infobox.rows
                .first { it.label?.plainText == "Health points" }
                .value.plainText,
        ).isEqualTo("20❤️ × 10")
    }

    @Test
    fun `Creeper keeps thumbnails with captions and tab content`() {
        val sections = parse(fixture("creeper"))
        val images = sections.allBlocks().filterIsInstance<ContentBlock.Image>()

        assertThat(images.map { it.caption?.plainText }).contains("A creeper giving chase.")
        assertThat(sections.allBlocks().filterIsInstance<ContentBlock.Paragraph>().map { it.text.plainText })
            .containsAtLeast("Decimal", "Fraction", "Distribution", "Expectation")
    }

    @Test
    fun `Crafting Table has a hatnote-led crafting section and a recipe`() {
        val sections = parse(fixture("crafting_table"))

        assertThat(
            sections
                .first()
                .blocks
                .filterIsInstance<ContentBlock.Infobox>()
                .single()
                .title,
        ).isEqualTo("Crafting Table")
        val allText =
            sections
                .allBlocks()
                .filterIsInstance<ContentBlock.Table>()
                .flatMap {
                    it.rows.flatten()
                }.map { it.content.plainText }
        assertThat(allText).contains("Oak Planks → Crafting Table")
        assertThat(sections.allBlocks().filterIsInstance<ContentBlock.Note>()).isNotEmpty()
    }

    @Test
    fun `tutorial page parses lists, headings and leaves wiki-only widgets unsupported`() {
        val sections = parse(fixture("tutorial_mining"))
        val blocks = sections.allBlocks()

        assertThat(sections.first().heading).isNull()
        assertThat(blocks.filterIsInstance<ContentBlock.ListBlock>()).isNotEmpty()
        assertThat(blocks.filterIsInstance<ContentBlock.Heading>()).isNotEmpty()
        val unsupported = blocks.filterIsInstance<ContentBlock.Unsupported>().map { it.htmlSnippet }
        assertThat(unsupported.any { "mcw-calc" in it }).isTrue()
        assertThat(unsupported.any { "embedvideo" in it }).isTrue()
    }

    @Test
    fun `every fixture yields only well-formed output`() {
        for (name in listOf("diamond", "creeper", "crafting_table", "tutorial_mining")) {
            val sections = parse(fixture(name))
            assertThat(sections).isNotEmpty()
            sections.allBlocks().forEach { block ->
                when (block) {
                    is ContentBlock.Paragraph -> assertThat(block.text.isBlank).isFalse()
                    is ContentBlock.Image -> assertThat(block.url).startsWith("http")
                    is ContentBlock.Table -> assertThat(block.rows).isNotEmpty()
                    is ContentBlock.ListBlock -> assertThat(block.items).isNotEmpty()
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun `parses the full Diamond page in under 300 ms`() {
        val html = fixture("diamond")

        val elapsed = measureTime { parse(html) }

        assertThat(elapsed.inWholeMilliseconds).isLessThan(300)
    }
}
