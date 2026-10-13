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
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.SeedParser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

internal const val SEED_FIELD_TAG = "seed-map-seed-field"
internal const val SAVE_SEED_TAG = "seed-map-save-seed"
internal const val LEGEND_BUTTON_TAG = "seed-map-legend"
internal const val RANDOM_SEED_TAG = "seed-map-random"
internal const val SUGGESTIONS_TAG = "seed-map-suggestions"
internal const val GO_TO_SUGGESTION_TAG = "seed-map-suggest-goto"
internal const val USE_SEED_SUGGESTION_TAG = "seed-map-suggest-seed"

internal fun savedSeedTag(saved: SavedSeed): String = "seed-map-saved-${saved.seed}-${saved.version}"

/**
 * The floating seed pill at the top of the map. Collapsed, it shows the seed (and its saved name) with save and
 * legend buttons; tapped, it opens a full-screen search whose rows go to typed coordinates, use the typed seed,
 * roll a random one or load a saved one ([seedSuggestions]).
 *
 * The text lives in a [TextFieldState] here, per the Search screen's gotcha. It holds the seed while collapsed;
 * a seed typed as words stays as typed rather than turning into its hash. New text is what the rows act on.
 */
@Composable
internal fun SeedSearchBar(
    state: SeedMapState,
    onSave: () -> Unit,
    onLegend: () -> Unit,
    onAction: (SeedMapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastSubmitted by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = shownSeed(state.seed, lastSubmitted)
    val text = rememberTextFieldState(initialText = shown)
    val searchState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val collapsed = searchState.currentValue == SearchBarValue.Collapsed

    // Collapsed, the pill names the seed on the map, whatever was typed into it and abandoned.
    LaunchedEffect(shown, collapsed) {
        if (collapsed && text.text.toString() != shown) text.setTextAndPlaceCursorAtEnd(shown)
    }
    // Opening, the seed is selected so the first key replaces it. Keyed on the target, not the current value,
    // so it is selected before the animation ends and anything typed meanwhile is kept.
    val opening = searchState.targetValue == SearchBarValue.Expanded
    LaunchedEffect(opening) {
        if (opening) text.edit { selection = TextRange(0, length) }
    }

    val typed = text.text.toString()
    val suggestions = seedSuggestions(query = if (typed == shown) "" else typed, saved = state.savedSeeds)

    fun run(suggestion: SeedSuggestion?) {
        when (suggestion) {
            is SeedSuggestion.GoTo -> {
                onAction(SeedMapAction.GoToCoordinates(suggestion.pos.x, suggestion.pos.z))
            }

            is SeedSuggestion.UseSeed -> {
                lastSubmitted = suggestion.text
                onAction(SeedMapAction.SubmitSeed(suggestion.text))
            }

            SeedSuggestion.Random -> {
                lastSubmitted = null
                onAction(SeedMapAction.SubmitSeed(""))
            }

            is SeedSuggestion.Saved -> {
                lastSubmitted = null
                onAction(SeedMapAction.LoadSavedSeed(suggestion.saved))
            }

            null -> {
                Unit
            }
        }
        focusManager.clearFocus()
        scope.launch { searchState.animateToCollapsed() }
    }

    val inputField: @Composable () -> Unit = {
        SeedInputField(
            text = text,
            searchState = searchState,
            collapsed = collapsed,
            saved = state.savedEntry,
            onSearch = { run(suggestions.firstOrNull { it is SeedSuggestion.GoTo || it is SeedSuggestion.UseSeed }) },
            onBack = { run(null) },
        )
    }
    // The pill's buttons sit over the field rather than in it: a press inside the field focuses it, which opens
    // the search on top of whatever the button opened.
    Box(modifier = modifier) {
        SearchBar(state = searchState, inputField = inputField, modifier = Modifier.fillMaxWidth())
        if (collapsed) {
            PillActions(
                saved = state.savedEntry,
                onSave = onSave,
                onUnsave = { state.savedEntry?.let { onAction(SeedMapAction.DeleteSavedSeed(it)) } },
                onLegend = onLegend,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            )
        }
    }
    ExpandedFullScreenSearchBar(state = searchState, inputField = inputField) {
        SuggestionList(
            suggestions = suggestions,
            onClick = ::run,
            onRemove = { onAction(SeedMapAction.DeleteSavedSeed(it)) },
        )
    }
}

/** What the collapsed pill shows: the seed as last typed when it still is the seed, else its number. */
internal fun shownSeed(
    seed: Long,
    lastSubmitted: String?,
): String = lastSubmitted?.trim()?.takeIf { it.isNotEmpty() && SeedParser.parse(it) == seed } ?: seed.toString()

@Composable
private fun SeedInputField(
    text: TextFieldState,
    searchState: SearchBarState,
    collapsed: Boolean,
    saved: SavedSeed?,
    onSearch: () -> Unit,
    onBack: () -> Unit,
) {
    SearchBarDefaults.InputField(
        textFieldState = text,
        searchBarState = searchState,
        onSearch = { onSearch() },
        modifier = Modifier.testTag(SEED_FIELD_TAG),
        placeholder = { Text(stringResource(R.string.seedmap_search_hint)) },
        leadingIcon = {
            if (collapsed) {
                WikiIcon(icon = WikiIcons.Search, contentDescription = null)
            } else {
                WikiIconButton(
                    icon = WikiIcons.ArrowBack,
                    contentDescription = stringResource(R.string.seedmap_search_close),
                    onClick = onBack,
                )
            }
        },
        // Always a lambda, empty when there is no name: the text field decides once, on its first composition,
        // whether it has a suffix, and never shows one that turns up later.
        suffix = {
            val label = saved?.label
            if (collapsed && label != null) {
                Text(
                    text = stringResource(R.string.seedmap_saved_name_suffix, label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingIcon = {
            if (collapsed) {
                // Room for the PillActions drawn over the field.
                Spacer(Modifier.width(PillActionsWidth))
            } else if (text.text.isNotEmpty()) {
                WikiIconButton(
                    icon = WikiIcons.Close,
                    contentDescription = stringResource(R.string.seedmap_search_clear),
                    onClick = { text.clearText() },
                )
            }
        },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
    )
}

/** Save (or unsave) and the legend, over the end of the collapsed pill. */
@Composable
private fun PillActions(
    saved: SavedSeed?,
    onSave: () -> Unit,
    onUnsave: () -> Unit,
    onLegend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        WikiIconButton(
            icon = if (saved != null) WikiIcons.Bookmark else WikiIcons.BookmarkBorder,
            contentDescription =
                stringResource(if (saved != null) R.string.seedmap_unsave_seed else R.string.seedmap_save_seed),
            onClick = if (saved != null) onUnsave else onSave,
            modifier = Modifier.testTag(SAVE_SEED_TAG),
        )
        WikiIconButton(
            icon = WikiIcons.FormatListBulleted,
            contentDescription = stringResource(R.string.seedmap_legend_open),
            onClick = onLegend,
            modifier = Modifier.testTag(LEGEND_BUTTON_TAG),
        )
    }
}

/** Two icon buttons, less the trailing slot's own padding. */
private val PillActionsWidth = 96.dp

@Composable
private fun SuggestionList(
    suggestions: ImmutableList<SeedSuggestion>,
    onClick: (SeedSuggestion) -> Unit,
    onRemove: (SavedSeed) -> Unit,
) {
    val hasSaved = suggestions.any { it is SeedSuggestion.Saved }
    LazyColumn(modifier = Modifier.testTag(SUGGESTIONS_TAG)) {
        items(suggestions, key = { it.key() }) { suggestion ->
            when (suggestion) {
                is SeedSuggestion.GoTo -> {
                    SuggestionRow(
                        icon = WikiIcons.PinDrop,
                        headline = stringResource(R.string.seedmap_suggest_go_to, suggestion.pos.x, suggestion.pos.z),
                        onClick = { onClick(suggestion) },
                        modifier = Modifier.testTag(GO_TO_SUGGESTION_TAG),
                    )
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
        if (!hasSaved) {
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

private fun SeedSuggestion.key(): String =
    when (this) {
        is SeedSuggestion.GoTo -> "goto"
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
