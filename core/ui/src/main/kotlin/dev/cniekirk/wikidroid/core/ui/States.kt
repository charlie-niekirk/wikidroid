package dev.cniekirk.wikidroid.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme

/** A centred spinner that fills its parent. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.ui_loading)
    Column(
        modifier = modifier.fillMaxSize().semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** An icon, a [title] and an optional [message] and action, centred in its parent. */
@Composable
fun MessageState(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        WikiIcon(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Nothing to show, for example no search results or an empty library. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    @DrawableRes icon: Int = WikiIcons.Search,
) {
    MessageState(icon = icon, title = title, modifier = modifier, message = message)
}

/**
 * A failure with a plain-language explanation. Offline gets its own icon. Pass [onRetry] to offer a
 * "Try again" button.
 */
@Composable
fun ErrorState(
    error: DataError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    MessageState(
        icon = if (error is DataError.Network && error.httpCode == null) WikiIcons.CloudOff else WikiIcons.Error,
        title = stringResource(R.string.ui_error_title),
        modifier = modifier,
        message = error.message(),
        actionLabel = onRetry?.let { stringResource(R.string.ui_retry) },
        onAction = onRetry,
    )
}

@Composable
private fun DataError.message(): String =
    when (this) {
        is DataError.Network -> {
            val code = httpCode
            if (code ==
                null
            ) {
                stringResource(R.string.ui_error_offline)
            } else {
                stringResource(R.string.ui_error_http, code)
            }
        }

        is DataError.Api -> {
            stringResource(R.string.ui_error_api, info ?: code)
        }

        DataError.NotFound -> {
            stringResource(R.string.ui_error_not_found)
        }

        DataError.Parse -> {
            stringResource(R.string.ui_error_parse)
        }

        is DataError.Unknown -> {
            stringResource(R.string.ui_error_unknown)
        }
    }

@Preview
@Composable
private fun ErrorStatePreview() {
    WikiDroidTheme(dynamicColor = false) {
        Surface {
            ErrorState(error = DataError.Network(), onRetry = {})
        }
    }
}

@Preview
@Composable
private fun EmptyStatePreview() {
    WikiDroidTheme(dynamicColor = false) {
        Surface {
            EmptyState(title = "No results", message = "Try a different search.")
        }
    }
}
