package dev.cniekirk.wikidroid.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiTopAppBar
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.ui.R as UiR

internal const val ABOUT_LIST_TAG = "about-list"

internal const val CONTENT_LICENCE_URL = "https://creativecommons.org/licenses/by-nc-sa/3.0/"
internal const val WIKI_URL = "https://minecraft.wiki"
internal const val SOURCE_URL = "https://github.com/charlie-niekirk/wikidroid"

/**
 * Version, the unofficial-app disclaimer, content attribution and open source licences.
 * Stateless: [onOpenUrl] and [onNavigateBack] are the route's job. [versionName] is `null` if it can't be read.
 */
@Composable
fun AboutScreen(
    versionName: String?,
    onOpenUrl: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            WikiTopAppBar(
                title = stringResource(R.string.about_title),
                onNavigateBack = onNavigateBack,
                navigateBackDescription = stringResource(UiR.string.ui_navigate_back),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.testTag(ABOUT_LIST_TAG),
            contentPadding = padding,
        ) {
            item(key = "header") { Header(versionName) }
            item(key = "disclaimer") {
                TextSection(
                    title = stringResource(R.string.about_disclaimer_title),
                    body = stringResource(R.string.about_disclaimer),
                )
            }
            item(key = "content") {
                TextSection(
                    title = stringResource(R.string.about_content_title),
                    body = stringResource(R.string.about_content),
                )
                LinkRow(stringResource(R.string.about_link_licence), CONTENT_LICENCE_URL, onOpenUrl)
                LinkRow(stringResource(R.string.about_link_wiki), WIKI_URL, onOpenUrl)
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
            item(key = "source") {
                SectionTitle(stringResource(R.string.about_source_title))
                LinkRow(stringResource(R.string.about_link_source), SOURCE_URL, onOpenUrl)
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
            item(key = "libraries-header") {
                SectionTitle(stringResource(R.string.about_libraries_title))
                Text(
                    text = stringResource(R.string.about_libraries_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(OpenSourceLibraries, key = { it.name }) { library ->
                LinkRow(title = library.name, url = library.url, onOpenUrl = onOpenUrl, summary = library.licence)
            }
            item(key = "bottom-space") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun Header(versionName: String?) {
    Column {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
            Text(text = stringResource(R.string.about_app_name), style = MaterialTheme.typography.headlineMedium)
            if (versionName != null) {
                Text(
                    text = stringResource(R.string.about_version, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun TextSection(
    title: String,
    body: String,
) {
    Column {
        SectionTitle(title)
        Text(text = body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun LinkRow(
    title: String,
    url: String,
    onOpenUrl: (String) -> Unit,
    summary: String? = null,
) {
    ListItem(
        onClick = { onOpenUrl(url) },
        modifier = Modifier.padding(horizontal = 8.dp),
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { WikiIcon(icon = WikiIcons.OpenInNew, contentDescription = null) },
    ) {
        Text(title)
    }
}

@PreviewLightDark
@Composable
private fun AboutScreenPreview() {
    WikiDroidTheme(dynamicColor = false) {
        AboutScreen(versionName = "1.0.0", onOpenUrl = {}, onNavigateBack = {})
    }
}
