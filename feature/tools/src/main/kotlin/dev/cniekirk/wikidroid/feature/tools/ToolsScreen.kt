package dev.cniekirk.wikidroid.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiTopAppBar
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal const val TOOLS_LIST_TAG = "tools-list"

/** The Tools tab: one card per tool. It has no state of its own, so there is no ViewModel. */
@Composable
fun ToolsScreen(
    onOpenTool: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    tools: ImmutableList<Tool> = Tool.entries.toImmutableList(),
) {
    Scaffold(
        modifier = modifier,
        topBar = { WikiTopAppBar(title = stringResource(R.string.tools_title)) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = CARD_MIN_WIDTH),
            modifier = Modifier.testTag(TOOLS_LIST_TAG),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tools, key = { it.name }) { tool -> ToolCard(tool = tool, onClick = { onOpenTool(tool) }) }
        }
    }
}

@Composable
private fun ToolCard(
    tool: Tool,
    onClick: () -> Unit,
) {
    ElevatedCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WikiIcon(
                icon = tool.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(tool.title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(tool.summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val CARD_MIN_WIDTH = 280.dp

@Preview
@Composable
private fun ToolsScreenPreview() {
    WikiDroidTheme(dynamicColor = false) {
        ToolsScreen(onOpenTool = {})
    }
}
