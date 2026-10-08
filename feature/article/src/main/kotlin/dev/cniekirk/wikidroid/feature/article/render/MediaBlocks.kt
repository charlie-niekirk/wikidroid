package dev.cniekirk.wikidroid.feature.article.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.Link

/** Sprites are enlarged to at least this wide, so a 16px item icon is not a speck. */
private val MinPixelatedWidth = 64.dp

/**
 * One wiki image. Its size comes from the parser's width and height (as CSS pixels, drawn as dp) so the
 * space is reserved before the bitmap arrives and the page doesn't jump. Sprites are scaled without
 * smoothing and enlarged to at least [MinPixelatedWidth].
 */
@Composable
internal fun ArticleImage(
    image: ContentBlock.Image,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
) {
    val size = image.knownSize()
    val sizing =
        if (size != null) {
            val (width, height) = size
            var cap = width.dp
            if (image.pixelated) cap = cap.coerceAtLeast(MinPixelatedWidth)
            if (maxWidth != Dp.Unspecified) cap = cap.coerceAtMost(maxWidth)
            Modifier.widthIn(max = cap).fillMaxWidth().aspectRatio(width.toFloat() / height)
        } else {
            val cap = if (maxWidth != Dp.Unspecified) maxWidth else Dp.Infinity
            Modifier.widthIn(max = cap).fillMaxWidth().heightIn(max = UnsizedImageMaxHeight)
        }
    var loaded by remember(image.url) { mutableStateOf(false) }
    val placeholder = if (loaded) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest
    AsyncImage(
        model = image.url,
        contentDescription = image.altText,
        modifier = modifier.then(sizing).background(placeholder),
        onSuccess = { loaded = true },
        contentScale = ContentScale.Fit,
        filterQuality = if (image.pixelated) FilterQuality.None else FilterQuality.Low,
    )
}

private val UnsizedImageMaxHeight = 320.dp

/** The parser's width and height, or `null` when either is missing or not positive. */
private fun ContentBlock.Image.knownSize(): Pair<Int, Int>? {
    val knownWidth = width ?: return null
    val knownHeight = height ?: return null
    return if (knownWidth > 0 && knownHeight > 0) knownWidth to knownHeight else null
}

@Composable
internal fun ImageBlock(
    image: ContentBlock.Image,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ArticleImage(image = image)
        image.caption?.takeUnless { it.isBlank }?.let { caption ->
            RichTextView(
                text = caption,
                onLinkClick = onLinkClick,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun GalleryBlock(
    gallery: ContentBlock.Gallery,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(gallery.images) { image ->
            Column(modifier = Modifier.width(GalleryItemWidth), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ArticleImage(image = image)
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

private val GalleryItemWidth = 144.dp
