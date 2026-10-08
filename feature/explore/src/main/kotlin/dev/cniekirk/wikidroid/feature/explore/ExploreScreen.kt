package dev.cniekirk.wikidroid.feature.explore

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiTopAppBar
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.LatestVersions
import dev.cniekirk.wikidroid.core.ui.userMessage
import dev.cniekirk.wikidroid.core.ui.R as UiR

internal const val VERSIONS_CARD_TAG = "explore-versions"
internal const val RANDOM_CARD_TAG = "explore-random"

/** The Explore tab. Stateless: the route owns the ViewModel, the snackbar host and navigation. */
@Composable
fun ExploreScreen(
    state: ExploreState,
    onAction: (ExploreAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = { WikiTopAppBar(title = stringResource(R.string.explore_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = CategoryTileMinWidth),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "versions", span = { GridItemSpan(maxLineSpan) }) {
                LatestVersionsCard(versions = state.versions, onAction = onAction)
            }
            item(key = "random", span = { GridItemSpan(maxLineSpan) }) {
                RandomArticleCard(
                    isLoading = state.isLoadingRandom,
                    onClick = { onAction(ExploreAction.OpenRandomArticle) },
                )
            }
            item(key = "browse-header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.explore_browse_by_category),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(state.categories, key = { it.name }) { category ->
                CategoryTile(
                    label = category.displayName,
                    onClick = { onAction(ExploreAction.OpenCategory(category.name)) },
                )
            }
        }
    }
}

private val CategoryTileMinWidth = 150.dp

@Composable
private fun LatestVersionsCard(
    versions: VersionsState,
    onAction: (ExploreAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().testTag(VERSIONS_CARD_TAG)) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.explore_latest_versions),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            when (versions) {
                VersionsState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }

                is VersionsState.Failed -> {
                    VersionsError(error = versions.error, onRetry = { onAction(ExploreAction.RetryVersions) })
                }

                is VersionsState.Loaded -> {
                    versions.versions.rows().forEach { row ->
                        VersionRow(
                            label = stringResource(row.labelRes),
                            version = row.version,
                            onClick = { onAction(ExploreAction.OpenArticle(row.pageTitle)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(
    label: String,
    version: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = version, style = MaterialTheme.typography.bodyLarge)
        }
        WikiIcon(
            icon = WikiIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VersionsError(
    error: DataError,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.explore_versions_unavailable),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = error.userMessage(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text(stringResource(UiR.string.ui_retry)) }
    }
}

@Composable
private fun RandomArticleCard(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier.fillMaxWidth().testTag(RANDOM_CARD_TAG),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                WikiIcon(icon = WikiIcons.Shuffle, contentDescription = null)
            }
            Column {
                Text(text = stringResource(R.string.explore_random_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.explore_random_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CategoryTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Start,
            )
        }
    }
}

private class VersionRowData(
    @StringRes val labelRes: Int,
    val version: String,
    val pageTitle: String,
)

/** Only the editions the wiki reported; each row opens that version's page. */
private fun LatestVersions.rows(): List<VersionRowData> =
    listOfNotNull(
        java?.let { VersionRowData(R.string.explore_version_java, it, "Java Edition $it") },
        javaSnapshot?.let { VersionRowData(R.string.explore_version_java_snapshot, it, "Java Edition $it") },
        bedrock?.let { VersionRowData(R.string.explore_version_bedrock, it, "Bedrock Edition $it") },
        bedrockPreview?.let { VersionRowData(R.string.explore_version_bedrock_preview, it, "Bedrock Edition $it") },
    )

@Preview
@Composable
private fun ExploreScreenPreview() {
    WikiDroidTheme(dynamicColor = false) {
        ExploreScreen(
            state =
                ExploreState(
                    versions =
                        VersionsState.Loaded(
                            LatestVersions(
                                java = "26.3",
                                javaSnapshot = "26.4 Snapshot 3",
                                bedrock = "26.52",
                                bedrockPreview = "Preview 26.60.30",
                            ),
                        ),
                ),
            onAction = {},
        )
    }
}
