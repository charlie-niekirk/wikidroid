package dev.cniekirk.wikidroid.feature.article.render

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.cniekirk.wikidroid.core.model.InlineImage
import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.model.RichSpan
import dev.cniekirk.wikidroid.core.model.RichText
import dev.cniekirk.wikidroid.core.model.TextStyleFlag

/**
 * [text] with its styling and links. A tapped link is reported to [onLinkClick]; it is the caller's job to
 * decide between opening another article and a browser.
 */
@Composable
internal fun RichTextView(
    text: RichText,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    // The AnnotatedString holds the click handler, so it must not change every time the caller's lambda does.
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val annotated =
        remember(text, linkColor, codeBackground) {
            text.toAnnotatedString(linkColor, codeBackground) { link -> currentOnLinkClick(link) }
        }
    val inlineContent = remember(text) { text.inlineContent() }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
        inlineContent = inlineContent,
    )
}

internal fun RichText.toAnnotatedString(
    linkColor: Color,
    codeBackground: Color,
    onLinkClick: (Link) -> Unit,
): AnnotatedString =
    buildAnnotatedString {
        spans.forEachIndexed { index, span ->
            val link = span.link
            if (link == null) {
                appendSpan(index, span, codeBackground)
            } else {
                val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                withLink(LinkAnnotation.Clickable(tag = link.tag(), styles = linkStyles) { onLinkClick(link) }) {
                    appendSpan(index, span, codeBackground)
                }
            }
        }
    }

private fun AnnotatedString.Builder.appendSpan(
    index: Int,
    span: RichSpan,
    codeBackground: Color,
) {
    if (span.image != null) {
        appendInlineContent(inlineId(index), span.text.ifBlank { REPLACEMENT_CHARACTER })
    } else {
        withStyle(span.styles.toSpanStyle(codeBackground)) { append(span.text) }
    }
}

private fun inlineId(index: Int) = "inline-$index"

/**
 * The pictures in this text, keyed the way [toAnnotatedString] refers to them. Sizes are in `sp`, so an icon
 * grows with the reader's text size and sits in the line like a letter.
 */
internal fun RichText.inlineContent(): Map<String, InlineTextContent> =
    buildMap {
        spans.forEachIndexed { index, span ->
            val image = span.image ?: return@forEachIndexed
            val (width, height) = image.placeholderSize()
            val description = span.text.takeIf { it.isNotBlank() }
            put(
                inlineId(index),
                InlineTextContent(Placeholder(width, height, PlaceholderVerticalAlign.TextCenter)) {
                    AsyncImage(
                        model = image.url,
                        contentDescription = description,
                        modifier = Modifier.fillMaxSize().padding(horizontal = ICON_GAP.dp / 2),
                        contentScale = ContentScale.Fit,
                        filterQuality = if (image.pixelated) FilterQuality.None else FilterQuality.Low,
                    )
                },
            )
        }
    }

/** The image's own size as `sp`, shrunk to fit [MAX_INLINE_SIZE], plus a small gap so it doesn't touch the text. */
private fun InlineImage.placeholderSize(): Pair<TextUnit, TextUnit> {
    val naturalWidth = (width ?: DEFAULT_INLINE_SIZE).toFloat()
    val naturalHeight = (height ?: width ?: DEFAULT_INLINE_SIZE).toFloat()
    val scale = minOf(1f, MAX_INLINE_SIZE / maxOf(naturalWidth, naturalHeight))
    return (naturalWidth * scale + ICON_GAP).sp to (naturalHeight * scale).sp
}

private const val DEFAULT_INLINE_SIZE = 16
private const val MAX_INLINE_SIZE = 96f
private const val ICON_GAP = 4f
private const val REPLACEMENT_CHARACTER = "\uFFFD"

private fun Link.tag(): String =
    when (this) {
        is Link.Internal -> if (anchor == null) title else "$title#$anchor"
        is Link.External -> url
    }

private fun Set<TextStyleFlag>.toSpanStyle(codeBackground: Color): SpanStyle {
    val decorations =
        buildList {
            if (TextStyleFlag.Underline in this@toSpanStyle) add(TextDecoration.Underline)
            if (TextStyleFlag.Strikethrough in this@toSpanStyle) add(TextDecoration.LineThrough)
        }
    val isScript = TextStyleFlag.Superscript in this || TextStyleFlag.Subscript in this
    return SpanStyle(
        fontWeight = if (TextStyleFlag.Bold in this) FontWeight.Bold else null,
        fontStyle = if (TextStyleFlag.Italic in this) FontStyle.Italic else null,
        fontFamily = if (TextStyleFlag.Code in this) FontFamily.Monospace else null,
        background = if (TextStyleFlag.Code in this) codeBackground else Color.Unspecified,
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        baselineShift =
            when {
                TextStyleFlag.Superscript in this -> BaselineShift.Superscript
                TextStyleFlag.Subscript in this -> BaselineShift.Subscript
                else -> null
            },
        fontSize = if (isScript) SCRIPT_FONT_SCALE.em else TextUnit.Unspecified,
    )
}

private const val SCRIPT_FONT_SCALE = 0.75f
