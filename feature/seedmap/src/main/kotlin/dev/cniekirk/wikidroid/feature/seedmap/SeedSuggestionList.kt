package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIconButton
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.model.PointOfInterest
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.SeedParser
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.seedmap.StructureWikiTitles
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

/** The rows under the expanded seed search ([seedSuggestions]), with the saved seeds' header. */
@Composable
internal fun SuggestionList(
    suggestions: ImmutableList<SeedSuggestion>,
    onClick: (SeedSuggestion) -> Unit,
    onRemove: (SavedSeed) -> Unit,
    onWorlds: () -> Unit,
) {
    val hasSaved = suggestions.any { it is SeedSuggestion.Saved }
    val firstSaved = suggestions.indexOfFirst { it is SeedSuggestion.Saved }
    LazyColumn(modifier = Modifier.testTag(SUGGESTIONS_TAG)) {
        // The saved seeds' header, with the way into My worlds, sits above the first saved seed (or the empty text).
        suggestions.forEachIndexed { index, suggestion ->
            if (index == firstSaved) item(key = "saved-header") { SavedHeader(onWorlds) }
            item(key = suggestion.key()) { SuggestionItem(suggestion, onClick, onRemove) }
        }
        if (!hasSaved) {
            item(key = "saved-header") { SavedHeader(onWorlds) }
            item(key = "no-saved") {
                Text(
                    text = stringResource(R.string.seedmap_no_saved_seeds),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Saved seeds", and the button that opens My worlds to rename and manage them. */
@Composable
private fun SavedHeader(onWorlds: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.seedmap_saved_seeds),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        TextButton(onClick = onWorlds, modifier = Modifier.testTag(WORLDS_BUTTON_TAG)) {
            WikiIcon(icon = WikiIcons.Public, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.seedmap_worlds_open))
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: SeedSuggestion,
    onClick: (SeedSuggestion) -> Unit,
    onRemove: (SavedSeed) -> Unit,
) {
    when (suggestion) {
        is SeedSuggestion.GoTo -> {
            SuggestionRow(
                icon = WikiIcons.PinDrop,
                headline = stringResource(R.string.seedmap_suggest_go_to, suggestion.pos.x, suggestion.pos.z),
                onClick = { onClick(suggestion) },
                modifier = Modifier.testTag(GO_TO_SUGGESTION_TAG),
            )
        }

        is SeedSuggestion.Nearest -> {
            ListItem(
                onClick = { onClick(suggestion) },
                modifier = Modifier.testTag(nearestSuggestionTag(suggestion.type)),
                leadingContent = {
                    MarkerIcon(suggestion.type.markerStyle(), icon = suggestion.type.icon(), size = 24.dp)
                },
                supportingContent = { Text(stringResource(R.string.seedmap_suggest_nearest_detail)) },
            ) {
                Text(
                    stringResource(
                        R.string.seedmap_suggest_nearest,
                        StructureWikiTitles.titleFor(suggestion.type),
                    ),
                )
            }
        }

        is SeedSuggestion.Poi -> {
            ListItem(
                onClick = { onClick(suggestion) },
                modifier = Modifier.testTag(poiSuggestionTag(suggestion.point)),
                leadingContent = {
                    PoiMarker(
                        suggestion.point.category.icon,
                        suggestion.point.color,
                        size = 24.dp,
                    )
                },
                supportingContent = {
                    Text(stringResource(R.string.seedmap_coordinates, suggestion.point.x, suggestion.point.z))
                },
            ) {
                Text(
                    stringResource(R.string.seedmap_suggest_poi, suggestion.point.displayName()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        is SeedSuggestion.UseSeed -> {
            SuggestionRow(
                icon = WikiIcons.Map,
                headline = stringResource(R.string.seedmap_suggest_use_seed, suggestion.text),
                onClick = { onClick(suggestion) },
                modifier = Modifier.testTag(USE_SEED_SUGGESTION_TAG),
            )
        }

        SeedSuggestion.Random -> {
            SuggestionRow(
                icon = WikiIcons.Shuffle,
                headline = stringResource(R.string.seedmap_random_seed),
                onClick = { onClick(suggestion) },
                modifier = Modifier.testTag(RANDOM_SEED_TAG),
            )
        }

        is SeedSuggestion.Saved -> {
            SavedSeedRow(suggestion.saved, onLoad = { onClick(suggestion) }, onRemove = onRemove)
        }
    }
}

private fun SeedSuggestion.key(): String =
    when (this) {
        is SeedSuggestion.GoTo -> "goto"
        is SeedSuggestion.Nearest -> "nearest-${type.name}"
        is SeedSuggestion.Poi -> "poi-${point.id}"
        is SeedSuggestion.UseSeed -> "seed"
        SeedSuggestion.Random -> "random"
        is SeedSuggestion.Saved -> savedSeedTag(saved)
    }

@Composable
private fun SuggestionRow(
    icon: Int,
    headline: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        onClick = onClick,
        modifier = modifier,
        leadingContent = { WikiIcon(icon = icon, contentDescription = null) },
    ) {
        Text(headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SavedSeedRow(
    saved: SavedSeed,
    onLoad: () -> Unit,
    onRemove: (SavedSeed) -> Unit,
) {
    ListItem(
        onClick = onLoad,
        modifier = Modifier.testTag(savedSeedTag(saved)),
        leadingContent = { WikiIcon(icon = WikiIcons.Bookmark, contentDescription = null) },
        supportingContent = {
            Text(stringResource(R.string.seedmap_saved_seed_subtitle, saved.seed.toString(), saved.version))
        },
        trailingContent = {
            WikiIconButton(
                icon = WikiIcons.Delete,
                contentDescription = stringResource(R.string.seedmap_remove_saved_seed, saved.displayName()),
                onClick = { onRemove(saved) },
            )
        },
    ) {
        Text(saved.displayName())
    }
}
