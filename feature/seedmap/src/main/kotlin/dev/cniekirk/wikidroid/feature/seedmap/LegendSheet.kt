package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.seedmap.BiomeLegendEntry
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.seedmap.StructureWikiTitles
import kotlinx.collections.immutable.ImmutableList

internal const val LEGEND_SHEET_TAG = "seed-map-legend-sheet"
internal const val LEGEND_SEARCH_TAG = "seed-map-legend-search"
internal const val LEGEND_EMPTY_TAG = "seed-map-legend-empty"

internal fun legendStructureTag(title: String): String = "seed-map-legend-structure-$title"

internal fun legendBiomeTag(id: Int): String = "seed-map-legend-biome-$id"

/** A row of the legend's structure list. [type] is null for the world spawn. */
internal data class LegendStructure(
    val type: StructureType?,
    val title: String,
    val icon: StructureIcon,
)

/** The structures (and the spawn, in the overworld) the legend lists for the world being shown. */
internal fun legendStructures(
    structures: List<StructureType>,
    dimension: Dimension,
    spawnTitle: String,
): List<LegendStructure> =
    buildList {
        if (dimension == Dimension.OVERWORLD) add(LegendStructure(null, spawnTitle, SpawnIcon))
        structures.forEach { add(LegendStructure(it, StructureWikiTitles.titleFor(it), it.icon())) }
    }

/** Rows whose name contains [query], ignoring case and surrounding blanks; a blank query keeps them all. */
internal fun List<LegendStructure>.matching(query: String): List<LegendStructure> =
    query.trim().let { q -> if (q.isEmpty()) this else filter { it.title.contains(q, ignoreCase = true) } }

internal fun ImmutableList<BiomeLegendEntry>.matching(query: String): List<BiomeLegendEntry> =
    query.trim().let { q -> if (q.isEmpty()) this else filter { it.title.contains(q, ignoreCase = true) } }

/**
 * The map's legend: the structures the world can show, each with the icon its pin wears, and the
 * biomes it can paint, each with its colour. A search field narrows both lists, and a row opens that
 * thing's wiki article.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegendSheet(
    state: SeedMapState,
    onOpenArticle: (title: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val query = rememberTextFieldState()
    val spawnTitle = stringResource(R.string.seedmap_legend_spawn)
    val structures =
        remember(state.world, spawnTitle) {
            legendStructures(StructureType.availableIn(state.world), state.dimension, spawnTitle)
        }
    val text = query.text.toString()
    val shownStructures = structures.matching(text)
    val shownBiomes = state.biomeLegend.matching(text)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(modifier = Modifier.testTag(LEGEND_SHEET_TAG)) {
            item(key = "title") { SheetTitle(R.string.seedmap_legend_title) }
            item(key = "search") {
                OutlinedTextField(
                    state = query,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag(LEGEND_SEARCH_TAG),
                    label = { Text(stringResource(R.string.seedmap_legend_search)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
            if (shownStructures.isEmpty() && shownBiomes.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.seedmap_legend_no_matches),
                        modifier = Modifier.padding(24.dp).testTag(LEGEND_EMPTY_TAG),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (shownStructures.isNotEmpty()) {
                item(key = "structures-header") { LegendHeader(R.string.seedmap_legend_structures) }
                items(shownStructures, key = { "s-${it.title}" }) { row ->
                    ListItem(
                        onClick = { onOpenArticle(row.title) },
                        modifier = Modifier.testTag(legendStructureTag(row.title)),
                        leadingContent = {
                            val style = row.type?.markerStyle() ?: SpawnMarkerStyle
                            MarkerIcon(style, icon = row.icon, size = 28.dp)
                        },
                    ) {
                        Text(row.title)
                    }
                }
            }
            if (shownBiomes.isNotEmpty()) {
                item(key = "biomes-header") { LegendHeader(R.string.seedmap_legend_biomes) }
                items(shownBiomes, key = { "b-${it.id}" }) { biome ->
                    ListItem(
                        onClick = { onOpenArticle(biome.title) },
                        modifier = Modifier.testTag(legendBiomeTag(biome.id)),
                        leadingContent = { BiomeSwatch(Color(biome.argb)) },
                    ) {
                        Text(biome.title)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendHeader(title: Int) {
    Text(
        text = stringResource(title),
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** A square of the biome's map colour, outlined so a pale biome still shows against the sheet. */
@Composable
private fun BiomeSwatch(color: Color) {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = RoundedCornerShape(6.dp),
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {}
}
