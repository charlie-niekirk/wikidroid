package dev.cniekirk.wikidroid.core.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Structured article content produced by the article parser and rendered natively. */
@Immutable
sealed interface ContentBlock {
    data class Heading(
        val level: Int,
        val text: String,
        val anchor: String,
    ) : ContentBlock

    data class Paragraph(
        val text: RichText,
    ) : ContentBlock

    /** A hatnote or other call-out that sits apart from the body text. */
    data class Note(
        val text: RichText,
    ) : ContentBlock

    data class ListBlock(
        val ordered: Boolean,
        val items: ImmutableList<ListItem>,
    ) : ContentBlock

    data class Image(
        val url: String,
        val width: Int? = null,
        val height: Int? = null,
        val caption: RichText? = null,
        val altText: String? = null,
        /** Sprites and pixel art, which must be scaled without smoothing. */
        val pixelated: Boolean = false,
    ) : ContentBlock

    data class Gallery(
        val images: ImmutableList<Image>,
    ) : ContentBlock

    data class Table(
        val caption: RichText?,
        val rows: ImmutableList<ImmutableList<TableCell>>,
    ) : ContentBlock

    data class Infobox(
        val title: String?,
        val images: ImmutableList<Image>,
        val rows: ImmutableList<InfoboxRow>,
    ) : ContentBlock

    /** A 3x3 crafting recipe. [slots] is row-major; `null` is an empty slot. */
    data class CraftingGrid(
        val slots: ImmutableList<ImmutableList<CraftingSlot?>>,
        val output: CraftingSlot?,
    ) : ContentBlock

    /** Markup the parser can't map; the UI offers to open the page on the wiki instead. */
    data class Unsupported(
        val htmlSnippet: String,
    ) : ContentBlock
}

@Immutable
data class ListItem(
    val text: RichText,
    val sublists: ImmutableList<ContentBlock.ListBlock> = persistentListOf(),
)

@Immutable
data class TableCell(
    val content: RichText,
    val isHeader: Boolean = false,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
)

/** A [label] of `null` means [value] spans the full width, which is how section headers appear. */
@Immutable
data class InfoboxRow(
    val label: RichText?,
    val value: RichText,
)

@Immutable
data class CraftingSlot(
    val name: String,
    val imageUrl: String? = null,
    val count: Int = 1,
    val link: Link.Internal? = null,
)
