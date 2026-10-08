package dev.cniekirk.wikidroid.feature.article.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.model.ListItem

/** A `h3` or deeper heading inside a section. The `h2` that opens a section is drawn by the article screen. */
@Composable
internal fun HeadingBlock(
    heading: ContentBlock.Heading,
    modifier: Modifier = Modifier,
) {
    Text(
        text = heading.text,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp).semantics { heading() },
        style = heading.level.headingStyle(),
    )
}

@Composable
private fun Int.headingStyle(): TextStyle =
    when (this) {
        2, 3 -> MaterialTheme.typography.titleLarge
        4 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }

@Composable
internal fun ParagraphBlock(
    paragraph: ContentBlock.Paragraph,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    RichTextView(
        text = paragraph.text,
        onLinkClick = onLinkClick,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge,
    )
}

/** A hatnote or message box: set apart from the body so it reads as a pointer, not as prose. */
@Composable
internal fun NoteBlock(
    note: ContentBlock.Note,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WikiIcon(icon = WikiIcons.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            RichTextView(
                text = note.text,
                onLinkClick = onLinkClick,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun ListBlock(
    list: ContentBlock.ListBlock,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
    depth: Int = 0,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        list.items.forEachIndexed { index, item ->
            ListEntry(
                marker = if (list.ordered) "${index + 1}." else BULLETS[depth % BULLETS.size],
                item = item,
                onLinkClick = onLinkClick,
                depth = depth,
            )
        }
    }
}

@Composable
private fun ListEntry(
    marker: String,
    item: ListItem,
    onLinkClick: (Link) -> Unit,
    depth: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = marker,
            modifier = Modifier.widthIn(min = 20.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.Start) {
            RichTextView(text = item.text, onLinkClick = onLinkClick, style = MaterialTheme.typography.bodyLarge)
            item.sublists.forEach { sublist -> ListBlock(sublist, onLinkClick, depth = depth + 1) }
        }
    }
}

private val BULLETS = listOf("•", "◦", "▪")
