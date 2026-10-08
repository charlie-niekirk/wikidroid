package dev.cniekirk.wikidroid.feature.article.render

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.CraftingSlot
import dev.cniekirk.wikidroid.core.model.InfoboxRow
import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.feature.article.R
import kotlinx.collections.immutable.ImmutableList

private val InfoboxImageWidth = 200.dp

/** The summary card at the top of most articles: pictures, then label/value rows. */
@Composable
internal fun InfoboxBlock(
    infobox: ContentBlock.Infobox,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            infobox.title?.takeUnless { it.isBlank() }?.let { title ->
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            InfoboxImages(infobox.images, onLinkClick)
            infobox.rows.forEach { row -> InfoboxRowView(row, onLinkClick) }
        }
    }
}

@Composable
private fun InfoboxImages(
    images: ImmutableList<ContentBlock.Image>,
    onLinkClick: (Link) -> Unit,
) {
    when (images.size) {
        0 -> {
            // No pictures, nothing to draw.
        }

        1 -> {
            ImageBlock(images.single(), onLinkClick)
        }

        else -> {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                items(images) { image ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Fixed width, natural height: the picture's own proportions decide how tall it is.
                        ArticleImage(
                            image = image,
                            maxWidth = InfoboxImageWidth,
                            modifier = Modifier.width(InfoboxImageWidth),
                        )
                        image.caption?.takeUnless { it.isBlank }?.let { caption ->
                            RichTextView(
                                text = caption,
                                onLinkClick = onLinkClick,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoboxRowView(
    row: InfoboxRow,
    onLinkClick: (Link) -> Unit,
) {
    val label = row.label
    if (label == null) {
        // A full-width row is a section header inside the infobox, such as "Properties".
        RichTextView(
            text = row.value,
            onLinkClick = onLinkClick,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(8.dp),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RichTextView(
                text = label,
                onLinkClick = onLinkClick,
                modifier = Modifier.weight(LABEL_WEIGHT),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RichTextView(
                text = row.value,
                onLinkClick = onLinkClick,
                modifier = Modifier.weight(VALUE_WEIGHT),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private const val LABEL_WEIGHT = 0.4f
private const val VALUE_WEIGHT = 0.6f

private val SlotSize = 48.dp

/** The smaller grid that fits a table cell. */
internal val CompactSlotSize = 30.dp

/** A 3x3 recipe: the grid, an arrow, then the result. A slot with a link opens that item's page. */
@Composable
internal fun CraftingGridBlock(
    grid: ContentBlock.CraftingGrid,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
    slotSize: Dp = SlotSize,
) {
    val description = stringResource(R.string.article_crafting_recipe)
    Row(
        modifier =
            modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics {
                contentDescription =
                    description
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SLOT_GAP)) {
            grid.slots.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(SLOT_GAP)) {
                    row.forEach { slot -> CraftingSlotView(slot, slotSize, onLinkClick) }
                }
            }
        }
        WikiIcon(
            icon = WikiIcons.ChevronRight,
            contentDescription = null,
            modifier =
                Modifier.size(slotSize * ARROW_SCALE),
        )
        CraftingSlotView(grid.output, slotSize, onLinkClick)
    }
}

private val SLOT_GAP = 4.dp
private const val ARROW_SCALE = 0.66f
private const val IMAGE_SCALE = 0.75f

@Composable
private fun CraftingSlotView(
    slot: CraftingSlot?,
    slotSize: Dp,
    onLinkClick: (Link) -> Unit,
) {
    val link = slot?.link
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier =
            Modifier
                .size(slotSize)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
                .then(
                    if (link != null) {
                        Modifier.clickable(role = Role.Button) { onLinkClick(link) }
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (slot != null) SlotContent(slot, slotSize)
    }
}

@Composable
private fun SlotContent(
    slot: CraftingSlot,
    slotSize: Dp,
) {
    val label = if (slot.count > 1) "${slot.count} × ${slot.name}" else slot.name
    // The slot is one thing to a screen reader: its name and count, not the image and the badge separately.
    Box(
        modifier =
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        if (slot.imageUrl != null) {
            AsyncImage(
                model = slot.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(slotSize * IMAGE_SCALE),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )
        } else {
            Text(
                text = slot.name,
                modifier = Modifier.padding(2.dp),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
        }
        if (slot.count > 1) {
            Text(
                text = slot.count.toString(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** Content the parser couldn't map. The reader is offered the page on the wiki, where it works. */
@Composable
internal fun UnsupportedBlock(
    onOpenOnWiki: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onOpenOnWiki,
        label = { Text(stringResource(R.string.article_unsupported_open_on_wiki)) },
        modifier = modifier,
        leadingIcon = {
            WikiIcon(
                icon = WikiIcons.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}
