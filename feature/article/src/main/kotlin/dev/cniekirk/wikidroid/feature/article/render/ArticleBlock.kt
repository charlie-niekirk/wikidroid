package dev.cniekirk.wikidroid.feature.article.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.Link

/** Draws one [ContentBlock]. Spacing between blocks is the list's job, not the block's. */
@Composable
internal fun ArticleBlock(
    block: ContentBlock,
    onLinkClick: (Link) -> Unit,
    onOpenOnWiki: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is ContentBlock.Heading -> HeadingBlock(block, modifier)
        is ContentBlock.Paragraph -> ParagraphBlock(block, onLinkClick, modifier)
        is ContentBlock.Note -> NoteBlock(block, onLinkClick, modifier)
        is ContentBlock.ListBlock -> ListBlock(block, onLinkClick, modifier)
        is ContentBlock.Image -> ImageBlock(block, onLinkClick, modifier)
        is ContentBlock.Gallery -> GalleryBlock(block, onLinkClick, modifier)
        is ContentBlock.Table -> TableBlock(block, onLinkClick, modifier)
        is ContentBlock.Infobox -> InfoboxBlock(block, onLinkClick, modifier)
        is ContentBlock.CraftingGrid -> CraftingGridBlock(block, onLinkClick, modifier)
        is ContentBlock.Unsupported -> UnsupportedBlock(onOpenOnWiki, modifier)
    }
}
