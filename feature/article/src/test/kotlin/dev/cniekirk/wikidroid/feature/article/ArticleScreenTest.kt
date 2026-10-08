package dev.cniekirk.wikidroid.feature.article

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.test.FakeImageLoaderEngine
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.Article
import dev.cniekirk.wikidroid.core.model.ArticleSection
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test

private fun fakeImageLoader(context: PlatformContext): ImageLoader {
    val engine = FakeImageLoaderEngine.Builder().default(ColorImage(Color.Red.toArgb())).build()
    return ImageLoader.Builder(context).components { add(engine) }.build()
}

class ArticleScreenTest : ComposeTest() {
    private val actions = mutableListOf<ArticleAction>()

    private fun loaded(
        article: Article = sampleArticle(),
        collapsed: Set<Int> = emptySet(),
        isBookmarked: Boolean = false,
        isTocVisible: Boolean = false,
        pendingAnchor: String? = null,
        textScale: Float = 1f,
    ) = ArticleState(
        title = article.title,
        phase = ArticlePhase.Loaded(article),
        isBookmarked = isBookmarked,
        collapsedSections = collapsed.let { set -> persistentSetOf(*set.toTypedArray()) },
        isTocVisible = isTocVisible,
        pendingAnchor = pendingAnchor,
        textScale = textScale,
    )

    private fun setScreen(state: ArticleState) {
        composeRule.setContent {
            setSingletonImageLoaderFactory(::fakeImageLoader)
            WikiDroidTheme(dynamicColor = false) {
                ArticleScreen(state = state, onAction = { actions += it })
            }
        }
        composeRule.waitForIdle()
    }

    // region phases

    @Test
    fun showsTheRequestedTitleAndASpinnerWhileLoading() {
        setScreen(ArticleState(title = "Diamond"))

        composeRule.onNodeWithText("Diamond").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Share").assertDoesNotExist()
    }

    @Test
    fun aFailureOffersARetry() {
        setScreen(ArticleState(title = "Diamond", phase = ArticlePhase.Failed(DataError.Network())))

        composeRule.onNodeWithText("Try again").performClick()

        assertThat(actions).containsExactly(ArticleAction.Retry)
    }

    @Test
    fun aMissingPageSaysSo() {
        setScreen(ArticleState(title = "Nothing", phase = ArticlePhase.Failed(DataError.NotFound)))

        composeRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun theBackArrowGoesBack() {
        setScreen(loaded())

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(actions).containsExactly(ArticleAction.Back)
    }

    // endregion

    // region content

    @Test
    fun showsTheLeadAndEachSectionWithItsBlocks() {
        setScreen(loaded())

        listOf(
            "A diamond is a mineral.",
            "Obtaining",
            "Found underground.",
            "Mining",
            "Use an iron pickaxe.",
            "Crafting",
        ).forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun aDisplayTitleReplacesTheRequestedOneInTheBar() {
        setScreen(loaded(sampleArticle().copy(displayTitle = "/give")))

        composeRule.onNodeWithText("/give").assertIsDisplayed()
    }

    @Test
    fun theAttributionFooterOpensThePageHistoryAndLicence() {
        setScreen(loaded())
        composeRule.onNodeWithTag(ARTICLE_LIST_TAG).performScrollToNode(hasText("View page"))

        composeRule.onNodeWithText("View page").performClick()
        composeRule.onNodeWithText("View history").performClick()

        assertThat(actions)
            .containsExactly(
                ArticleAction.OpenUrl("https://minecraft.wiki/w/Diamond"),
                ArticleAction.OpenUrl("https://minecraft.wiki/w/Diamond?action=history"),
            ).inOrder()
    }

    @Test
    fun tappingALinkReportsIt() {
        val link = Link.Internal("Diamond ore")
        val article =
            sampleArticle().copy(
                sections =
                    persistentListOf(
                        ArticleSection(null, persistentListOf(ContentBlock.Paragraph(linked("Diamond ore", link)))),
                    ),
            )
        setScreen(loaded(article))

        composeRule.onNodeWithText("Diamond ore").performTouchInput { click(Offset(8f, centerY)) }

        assertThat(actions).containsExactly(ArticleAction.OpenLink(link))
    }

    @Test
    fun anUnsupportedBlockOffersTheWiki() {
        val article =
            sampleArticle().copy(
                sections = persistentListOf(ArticleSection(null, persistentListOf(ContentBlock.Unsupported("<div/>")))),
            )
        setScreen(loaded(article))

        composeRule.onNodeWithText("Can't show this here. Open on wiki").performClick()

        assertThat(actions).containsExactly(ArticleAction.OpenOnWiki)
    }

    @Test
    fun largerTextMakesTheArticleTaller() {
        var scale by mutableFloatStateOf(1f)
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                ArticleScreen(state = loaded(textScale = scale), onAction = {})
            }
        }
        val before =
            composeRule
                .onNodeWithText("A diamond is a mineral.")
                .fetchSemanticsNode()
                .size.height

        scale = 1.5f
        composeRule.waitForIdle()
        val after =
            composeRule
                .onNodeWithText("A diamond is a mineral.")
                .fetchSemanticsNode()
                .size.height

        assertThat(after).isGreaterThan(before)
    }

    // endregion

    // region folding

    @Test
    fun tappingASectionTitleFoldsIt() {
        setScreen(loaded())

        composeRule.onNodeWithText("Obtaining").performClick()

        assertThat(actions).containsExactly(ArticleAction.ToggleSection(1))
    }

    @Test
    fun aFoldedSectionHidesItsBlocksButNotItsTitle() {
        setScreen(loaded(collapsed = setOf(1)))

        composeRule.onNodeWithText("Obtaining").assertIsDisplayed()
        composeRule.onNodeWithText("Found underground.").assertDoesNotExist()
        composeRule.onNodeWithText("Mining").assertDoesNotExist()
        composeRule.onNodeWithText("Made from a block.").assertIsDisplayed()
    }

    @Test
    fun theSectionTitleSaysWhatTappingItWillDo() {
        setScreen(loaded(collapsed = setOf(1)))

        // Obtaining is folded, so tapping unfolds it; Crafting is open, so tapping folds it.
        assertThat(clickLabelOf("Obtaining")).isEqualTo("Expand section")
        assertThat(clickLabelOf("Crafting")).isEqualTo("Collapse section")
    }

    private fun clickLabelOf(text: String): String? =
        composeRule
            .onNodeWithText(text)
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .label

    // endregion

    // region top bar

    @Test
    fun theBookmarkButtonReflectsAndTogglesTheSavedState() {
        setScreen(loaded(isBookmarked = false))

        composeRule.onNodeWithContentDescription("Save for offline reading").performClick()

        assertThat(actions).containsExactly(ArticleAction.ToggleBookmark)
    }

    @Test
    fun aSavedArticleOffersToRemoveTheBookmark() {
        setScreen(loaded(isBookmarked = true))

        composeRule.onNodeWithContentDescription("Remove from saved").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Save for offline reading").assertDoesNotExist()
    }

    @Test
    fun theShareButtonShares() {
        setScreen(loaded())

        composeRule.onNodeWithContentDescription("Share").performClick()

        assertThat(actions).containsExactly(ArticleAction.Share)
    }

    @Test
    fun theOverflowMenuOpensThePageOnTheWiki() {
        setScreen(loaded())

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Open on wiki").performClick()

        assertThat(actions).containsExactly(ArticleAction.OpenOnWiki)
    }

    @Test
    fun withoutAnAddressThereIsNothingToShareOrOpen() {
        setScreen(loaded(sampleArticle(pageUrl = null)))

        composeRule.onNodeWithContentDescription("Share").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More options").assertDoesNotExist()
    }

    @Test
    fun aPageWithoutHeadingsHasNoContentsButton() {
        val flat =
            sampleArticle().copy(
                sections = persistentListOf(ArticleSection(null, persistentListOf(paragraph("Just text.")))),
            )
        setScreen(loaded(flat))

        composeRule.onNodeWithContentDescription("Table of contents").assertDoesNotExist()
    }

    // endregion

    // region contents sheet and anchors

    @Test
    fun theContentsButtonOpensTheSheet() {
        setScreen(loaded())

        composeRule.onNodeWithContentDescription("Table of contents").performClick()

        assertThat(actions).containsExactly(ArticleAction.ShowToc)
    }

    @Test
    fun theContentsSheetListsEveryHeadingAndJumpsToOne() {
        setScreen(loaded(isTocVisible = true))

        composeRule.onNodeWithText("Contents").assertIsDisplayed()
        composeRule.onNodeWithTag(TOC_LIST_TAG).assertIsDisplayed()
        // "Mining" is also a heading in the article behind the sheet; the sheet's entry is the clickable one.
        composeRule.onNode(hasText("Mining") and hasClickAction()).performClick()

        assertThat(actions).containsExactly(ArticleAction.GoToAnchor("Mining"))
    }

    @Test
    fun scrollsToAPendingAnchorAndReportsItDone() {
        // Enough blocks that "Crafting" starts well below the fold.
        val long =
            sampleArticle().copy(
                sections =
                    persistentListOf(
                        ArticleSection(null, persistentListOf(paragraph("Lead."))),
                        ArticleSection(
                            heading("Obtaining"),
                            List(40) {
                                paragraph("Filler paragraph $it.")
                            }.let { persistentListOf(*it.toTypedArray()) },
                        ),
                        ArticleSection(heading("Crafting"), persistentListOf(paragraph("Made from a block."))),
                    ),
            )
        setScreen(loaded(long, pendingAnchor = "Crafting"))

        composeRule.onNodeWithText("Made from a block.").assertIsDisplayed()
        composeRule.onNodeWithText("Lead.").assertDoesNotExist()
        assertThat(actions).containsExactly(ArticleAction.AnchorHandled)
    }

    @Test
    fun anAnchorThatCannotBeFoundIsStillClearedSoItDoesNotStick() {
        setScreen(loaded(pendingAnchor = "Nowhere"))

        assertThat(actions).containsExactly(ArticleAction.AnchorHandled)
    }

    // endregion
}
