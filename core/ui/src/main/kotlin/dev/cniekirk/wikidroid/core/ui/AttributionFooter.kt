package dev.cniekirk.wikidroid.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme

const val LICENSE_URL = "https://creativecommons.org/licenses/by-nc-sa/3.0/"

/** The link to an article's revision history, derived from its [pageUrl]. */
fun historyUrl(pageUrl: String): String = "$pageUrl${if ('?' in pageUrl) '&' else '?'}action=history"

/**
 * The CC BY-NC-SA credit that ends every article, with links to the page, its history and the licence.
 * Links are handed to [onOpenUrl], which decides how to open them (Custom Tabs in the article screen).
 * Without a [pageUrl] only the licence link is shown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttributionFooter(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    pageUrl: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        HorizontalDivider()
        Text(
            text = stringResource(R.string.ui_attribution_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (pageUrl != null) {
                TextButton(onClick = { onOpenUrl(pageUrl) }) { Text(stringResource(R.string.ui_attribution_view_page)) }
                TextButton(onClick = { onOpenUrl(historyUrl(pageUrl)) }) {
                    Text(stringResource(R.string.ui_attribution_view_history))
                }
            }
            TextButton(onClick = { onOpenUrl(LICENSE_URL) }) { Text(stringResource(R.string.ui_attribution_license)) }
        }
    }
}

@Preview
@Composable
private fun AttributionFooterPreview() {
    WikiDroidTheme(dynamicColor = false) {
        AttributionFooter(onOpenUrl = {}, pageUrl = "https://minecraft.wiki/w/Diamond")
    }
}
