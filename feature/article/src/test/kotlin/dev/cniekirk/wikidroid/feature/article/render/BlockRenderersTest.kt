package dev.cniekirk.wikidroid.feature.article.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.test.FakeImageLoaderEngine
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.CraftingSlot
import dev.cniekirk.wikidroid.core.model.InfoboxRow
import dev.cniekirk.wikidroid.core.model.InlineImage
import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.model.ListItem
import dev.cniekirk.wikidroid.core.model.RichSpan
import dev.cniekirk.wikidroid.core.model.RichText
import dev.cniekirk.wikidroid.core.model.TableCell
import dev.cniekirk.wikidroid.core.model.TextStyleFlag
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import dev.cniekirk.wikidroid.feature.article.heading
import dev.cniekirk.wikidroid.feature.article.linked
import dev.cniekirk.wikidroid.feature.article.paragraph
import dev.cniekirk.wikidroid.feature.article.text
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

private fun fakeImageLoader(context: PlatformContext): ImageLoader {
    val engine = FakeImageLoaderEngine.Builder().default(ColorImage(Color.Red.toArgb())).build()
    return ImageLoader.Builder(context).components { add(engine) }.build()
}

private const val LINK_TAP_X = 8f

class BlockRenderersTest : ComposeTest() {
    private val links = mutableListOf<Link>()
    private var openedOnWiki = 0

    private fun setBlock(block: ContentBlock) {
        composeRule.setContent {
            setSingletonImageLoaderFactory(::fakeImageLoader)
            WikiDroidTheme(dynamicColor = false) {
                ArticleBlock(block = block, onLinkClick = { links += it }, onOpenOnWiki = { openedOnWiki++ })
            }
        }
        composeRule.waitForIdle()
    }

    /** A link's text is narrower than the full-width node that holds it, so tap its left edge, not the centre. */
    private fun clickLink(label: String) {
        composeRule.onNodeWithText(label).performTouchInput { click(Offset(LINK_TAP_X, centerY)) }
    }

    // region text

    @Test
    fun aHeadingIsDrawnAndMarkedAsOneForAccessibility() {
        setBlock(heading("Mining", level = 3))

        composeRule
            .onNodeWithText("Mining")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun aParagraphShowsItsText() {
        setBlock(paragraph("A diamond is a mineral."))

        composeRule.onNodeWithText("A diamond is a mineral.").assertIsDisplayed()
    }

    @Test
    fun tappingALinkInAParagraphReportsTheLink() {
        val link = Link.Internal("Diamond ore", anchor = "Generation")
        setBlock(ContentBlock.Paragraph(linked("Diamond ore", link)))

        clickLink("Diamond ore")

        assertThat(links).containsExactly(link)
    }

    @Test
    fun tappingAnExternalLinkReportsIt() {
        val link = Link.External("https://example.com")
        setBlock(ContentBlock.Paragraph(linked("Example", link)))

        clickLink("Example")

        assertThat(links).containsExactly(link)
    }

    @Test
    fun aNoteShowsItsText() {
        setBlock(ContentBlock.Note(text("For other uses, see Diamond (disambiguation).")))

        composeRule.onNodeWithText("For other uses, see Diamond (disambiguation).").assertIsDisplayed()
    }

    @Test
    fun anOrderedListIsNumbered() {
        setBlock(
            ContentBlock.ListBlock(
                ordered = true,
                items = persistentListOf(ListItem(text("First")), ListItem(text("Second"))),
            ),
        )

        composeRule.onNodeWithText("1.").assertIsDisplayed()
        composeRule.onNodeWithText("2.").assertIsDisplayed()
        composeRule.onNodeWithText("Second").assertIsDisplayed()
    }

    @Test
    fun aNestedListIsDrawnUnderItsItem() {
        val nested = ContentBlock.ListBlock(ordered = false, items = persistentListOf(ListItem(text("Inner"))))
        setBlock(
            ContentBlock.ListBlock(
                ordered = false,
                items = persistentListOf(ListItem(text("Outer"), sublists = persistentListOf(nested))),
            ),
        )

        composeRule.onNodeWithText("Outer").assertIsDisplayed()
        composeRule.onNodeWithText("Inner").assertIsDisplayed()
        composeRule.onNodeWithText("•").assertIsDisplayed()
        composeRule.onNodeWithText("◦").assertIsDisplayed()
    }

    @Test
    fun aParagraphDrawsAnInlineImageBesideItsText() {
        val sprite = InlineImage("https://minecraft.wiki/images/ItemSprite_diamond.png", 16, 16, pixelated = true)
        setBlock(
            ContentBlock.Paragraph(
                RichText.of(RichSpan("Diamond sprite", image = sprite), RichSpan(" Diamond")),
            ),
        )

        composeRule.onNodeWithContentDescription("Diamond sprite").assertIsDisplayed()
        composeRule.onNodeWithText("Diamond", substring = true).assertIsDisplayed()
    }

    @Test
    fun anInlineImageWithoutAltTextStillGetsARoomInTheLine() {
        val sprite = InlineImage("https://minecraft.wiki/images/ItemSprite_diamond.png", 16, 16)
        val text = RichText.of(RichSpan("Item "), RichSpan("", image = sprite), RichSpan(" Diamond"))

        val annotated = text.toAnnotatedString(Color.Blue, Color.Gray) {}
        val content = text.inlineContent()

        assertThat(content.keys).containsExactly("inline-1")
        assertThat(
            annotated.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, annotated.length),
        ).hasSize(1)
        assertThat(annotated.text).startsWith("Item ")
        assertThat(annotated.text).endsWith(" Diamond")
    }

    @Test
    fun anInlineImageInsideALinkStaysClickable() {
        val link = Link.Internal("Diamond")
        val sprite = InlineImage("https://minecraft.wiki/images/ItemSprite_diamond.png", 16, 16)
        val annotated =
            RichText
                .of(RichSpan("", link = link, image = sprite), RichSpan("Diamond", link = link))
                .toAnnotatedString(Color.Blue, Color.Gray) {}

        assertThat(annotated.getLinkAnnotations(0, annotated.length)).isNotEmpty()
    }

    @Test
    fun aTableCellShowsAnIconAndItsLabel() {
        val sprite = InlineImage("https://minecraft.wiki/images/EnvSprite_mineshaft.png", 16, 16, pixelated = true)
        setBlock(
            ContentBlock.Table(
                caption = null,
                rows =
                    persistentListOf(
                        persistentListOf(
                            TableCell(RichText.of(RichSpan("Mineshaft icon", image = sprite), RichSpan("Mineshaft"))),
                        ),
                    ),
            ),
        )

        composeRule.onNodeWithContentDescription("Mineshaft icon").assertIsDisplayed()
        composeRule.onNodeWithText("Mineshaft", substring = true).assertIsDisplayed()
    }

    // endregion

    // region media

    @Test
    fun anImageHasItsAltTextAndCaption() {
        setBlock(
            ContentBlock.Image(
                url = "https://minecraft.wiki/images/Diamond.png",
                width = 150,
                height = 150,
                caption = text("A cut diamond."),
                altText = "Diamond",
                pixelated = true,
            ),
        )

        composeRule.onNodeWithContentDescription("Diamond").assertIsDisplayed()
        composeRule.onNodeWithText("A cut diamond.").assertIsDisplayed()
    }

    @Test
    fun anImageWithoutSizeStillComposes() {
        setBlock(ContentBlock.Image(url = "https://minecraft.wiki/images/Wide.png", altText = "Wide"))

        composeRule.onNodeWithContentDescription("Wide").assertIsDisplayed()
    }

    @Test
    fun aGalleryShowsEachCaption() {
        fun image(name: String) =
            ContentBlock.Image(url = "https://minecraft.wiki/images/$name.png", caption = text(name), altText = name)
        setBlock(ContentBlock.Gallery(persistentListOf(image("Overworld"), image("Nether"))))

        composeRule.onNodeWithText("Overworld").assertIsDisplayed()
        composeRule.onNodeWithText("Nether").assertIsDisplayed()
    }

    // endregion

    // region tables

    @Test
    fun aTableShowsHeadersAndCells() {
        setBlock(
            ContentBlock.Table(
                caption = text("Drops"),
                rows =
                    persistentListOf(
                        persistentListOf(
                            TableCell(text("Item"), isHeader = true),
                            TableCell(text("Chance"), isHeader = true),
                        ),
                        persistentListOf(TableCell(text("Diamond")), TableCell(text("100%"))),
                    ),
            ),
        )

        listOf("Drops", "Item", "Chance", "Diamond", "100%").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun aRowspanCellIsDrawnOnceAndItsRowsStillLineUp() {
        setBlock(
            ContentBlock.Table(
                caption = null,
                rows =
                    persistentListOf(
                        persistentListOf(TableCell(text("Tall"), rowSpan = 2), TableCell(text("a"))),
                        persistentListOf(TableCell(text("b"))),
                    ),
            ),
        )

        composeRule.onNodeWithText("Tall").assertIsDisplayed()
        val a = composeRule.onNodeWithText("a").fetchSemanticsNode().boundsInRoot
        val b = composeRule.onNodeWithText("b").fetchSemanticsNode().boundsInRoot
        assertThat(b.left).isEqualTo(a.left)
        assertThat(b.top).isGreaterThan(a.top)
        val tall = composeRule.onNodeWithText("Tall").fetchSemanticsNode().boundsInRoot
        assertThat(tall.top).isEqualTo(a.top)
        assertThat(tall.right).isLessThan(a.left)
    }

    @Test
    fun aLinkInATableCellIsClickable() {
        val link = Link.Internal("Mineshaft")
        setBlock(
            ContentBlock.Table(
                caption = null,
                rows = persistentListOf(persistentListOf(TableCell(linked("Mineshaft", link)))),
            ),
        )

        composeRule.onNodeWithText("Mineshaft").performClick()

        assertThat(links).containsExactly(link)
    }

    @Test
    fun aRecipeInATableCellIsDrawnAsAGrid() {
        val grid = craftingGrid()
        setBlock(
            ContentBlock.Table(
                caption = null,
                rows =
                    persistentListOf(
                        persistentListOf(TableCell(text("Planks → Crafting Table"), crafting = grid)),
                    ),
            ),
        )

        composeRule.onNodeWithContentDescription("Crafting recipe").assertIsDisplayed()
        composeRule.onNodeWithText("Planks → Crafting Table").assertDoesNotExist()
    }

    // endregion

    // region structured

    @Test
    fun anInfoboxShowsItsTitleRowsAndSectionHeaders() {
        setBlock(
            ContentBlock.Infobox(
                title = "Diamond",
                images = persistentListOf(),
                rows =
                    persistentListOf(
                        InfoboxRow(label = null, value = text("Properties")),
                        InfoboxRow(label = text("Rarity"), value = text("Common")),
                        InfoboxRow(label = text("Stackable"), value = linked("Yes (64)", Link.Internal("Stack"))),
                    ),
            ),
        )

        listOf("Diamond", "Properties", "Rarity", "Common", "Stackable").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        clickLink("Yes (64)")
        assertThat(links).containsExactly(Link.Internal("Stack"))
    }

    @Test
    fun anInfoboxWithSeveralPicturesCaptionsEachOne() {
        fun image(name: String) =
            ContentBlock.Image(url = "https://minecraft.wiki/images/$name.png", caption = text(name), altText = name)
        setBlock(
            ContentBlock.Infobox(
                title = "Creeper",
                images = persistentListOf(image("Normal"), image("Charged")),
                rows = persistentListOf(),
            ),
        )

        composeRule.onNodeWithText("Normal").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Normal").assertIsDisplayed()
    }

    private fun craftingGrid(): ContentBlock.CraftingGrid {
        val planks =
            CraftingSlot(
                "Oak Planks",
                imageUrl = "https://minecraft.wiki/images/Planks.png",
                link = Link.Internal("Oak Planks"),
            )
        val empty = persistentListOf<CraftingSlot?>(null, null, null)
        return ContentBlock.CraftingGrid(
            slots =
                persistentListOf(
                    persistentListOf(planks, planks, null),
                    persistentListOf(planks, planks, null),
                    empty,
                ),
            output = CraftingSlot("Crafting Table", count = 2, link = Link.Internal("Crafting Table")),
        )
    }

    @Test
    fun aCraftingGridNamesItsSlotsAndTheResult() {
        setBlock(craftingGrid())

        composeRule.onNodeWithContentDescription("Crafting recipe").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2 × Crafting Table").assertIsDisplayed()
        assertThat(composeRule.onAllNodes(hasContentDescription("Oak Planks")).fetchSemanticsNodes()).hasSize(4)
    }

    @Test
    fun tappingTheResultOfARecipeOpensThatItem() {
        setBlock(craftingGrid())

        composeRule.onNodeWithContentDescription("2 × Crafting Table").performClick()

        assertThat(links).containsExactly(Link.Internal("Crafting Table"))
    }

    @Test
    fun anUnsupportedBlockOffersTheWiki() {
        setBlock(ContentBlock.Unsupported("<div class=\"mcw-calc\"></div>"))

        composeRule.onNodeWithText("Can't show this here. Open on wiki").performClick()

        assertThat(openedOnWiki).isEqualTo(1)
    }

    // endregion

    // region rich text

    @Test
    fun richTextCarriesItsLinksAsAnnotations() {
        val link = Link.Internal("Diamond ore")
        val annotated =
            RichText
                .of(
                    RichSpan("Mine "),
                    RichSpan("diamond ore", link = link),
                ).toAnnotatedString(Color.Blue, Color.Gray) {}

        val annotations = annotated.getLinkAnnotations(0, annotated.length)

        assertThat(annotated.text).isEqualTo("Mine diamond ore")
        assertThat(annotations).hasSize(1)
        assertThat(annotations.single().start).isEqualTo("Mine ".length)
        assertThat(annotations.single().end).isEqualTo(annotated.length)
    }

    @Test
    fun richTextStylesBecomeSpanStyles() {
        val styled =
            RichText
                .of(
                    RichSpan(
                        "bold",
                        setOf(
                            TextStyleFlag.Bold,
                            TextStyleFlag.Superscript,
                        ),
                    ),
                ).toAnnotatedString(Color.Blue, Color.Gray) {}

        val style = styled.spanStyles.single().item

        assertThat(style.fontWeight?.weight).isEqualTo(700)
        assertThat(style.baselineShift).isNotNull()
    }

    // endregion
}
