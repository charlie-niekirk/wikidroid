package dev.cniekirk.wikidroid.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons

/**
 * A page's picture in a rounded tile, with a placeholder icon while it loads, if it fails, or when there is
 * no [url]. Wiki imagery is mostly pixel art, so [pixelated] (the default) scales without smoothing, which
 * keeps sprites crisp; turn it off for photographs and renders.
 *
 * Images load through Coil's singleton `ImageLoader`, which the app points at the shared OkHttp client.
 */
@Composable
fun PageThumbnail(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = DefaultThumbnailSize,
    pixelated: Boolean = true,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            WikiIcon(
                icon = WikiIcons.Image,
                contentDescription = null,
                modifier = Modifier.size(size / 2),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { loaded = true },
                contentScale = ContentScale.Fit,
                filterQuality = if (pixelated) FilterQuality.None else FilterQuality.Low,
            )
        }
    }
}

val DefaultThumbnailSize: Dp = 56.dp
