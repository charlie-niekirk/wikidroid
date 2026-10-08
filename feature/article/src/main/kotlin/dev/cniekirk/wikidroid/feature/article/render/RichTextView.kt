package dev.cniekirk.wikidroid.feature.article.render

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
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
import androidx.compose.ui.unit.em
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
    Text(text = annotated, modifier = modifier, style = style, color = color, textAlign = textAlign)
}

internal fun RichText.toAnnotatedString(
    linkColor: Color,
    codeBackground: Color,
    onLinkClick: (Link) -> Unit,
): AnnotatedString =
    buildAnnotatedString {
        spans.forEach { span ->
            val link = span.link
            if (link == null) {
                appendStyled(span, codeBackground)
            } else {
                val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                withLink(LinkAnnotation.Clickable(tag = link.tag(), styles = linkStyles) { onLinkClick(link) }) {
                    appendStyled(span, codeBackground)
                }
            }
        }
    }

private fun AnnotatedString.Builder.appendStyled(
    span: RichSpan,
    codeBackground: Color,
) {
    withStyle(span.styles.toSpanStyle(codeBackground)) { append(span.text) }
}

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
