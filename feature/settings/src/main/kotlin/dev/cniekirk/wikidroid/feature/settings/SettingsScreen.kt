package dev.cniekirk.wikidroid.feature.settings

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiTopAppBar
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.Edition
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.model.UserPreferences
import dev.cniekirk.wikidroid.core.ui.LoadingState
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.roundToInt

internal const val SETTINGS_LIST_TAG = "settings-list"
internal const val TEXT_SIZE_SLIDER_TAG = "text-size-slider"

/** Slider positions between the smallest and largest text size, in 5% steps. */
private const val TEXT_SCALE_STEPS = 12

private val TextScaleRange = UserPreferences.MIN_TEXT_SCALE..UserPreferences.MAX_TEXT_SCALE

/**
 * The Settings tab. Stateless: the route owns the ViewModel and navigation.
 * [dynamicColorSupported] is false before Android 12, where the wallpaper colours don't exist.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageSnackbar(message = state.message, snackbarHostState = snackbarHostState, onAction = onAction)

    Scaffold(
        modifier = modifier,
        topBar = { WikiTopAppBar(title = stringResource(R.string.settings_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val preferences = state.preferences
        if (preferences == null) {
            LoadingState(modifier = Modifier.padding(padding))
        } else {
            SettingsList(
                preferences = preferences,
                dynamicColorSupported = dynamicColorSupported,
                onAction = onAction,
                contentPadding = padding,
            )
        }
    }

    state.pendingClear?.let { target ->
        ClearDialog(
            target = target,
            onConfirm = { onAction(SettingsAction.ConfirmClear) },
            onDismiss = { onAction(SettingsAction.DismissClear) },
        )
    }
}

@Composable
private fun SettingsList(
    preferences: UserPreferences,
    dynamicColorSupported: Boolean,
    onAction: (SettingsAction) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(modifier = Modifier.testTag(SETTINGS_LIST_TAG), contentPadding = contentPadding) {
        item(key = "appearance") {
            Section(title = stringResource(R.string.settings_section_appearance)) {
                ThemeSetting(selected = preferences.themeMode, onSelect = { onAction(SettingsAction.SetThemeMode(it)) })
                if (dynamicColorSupported) {
                    SwitchSetting(
                        title = stringResource(R.string.settings_dynamic_color),
                        summary = stringResource(R.string.settings_dynamic_color_summary),
                        checked = preferences.dynamicColor,
                        onCheckedChange = { onAction(SettingsAction.SetDynamicColor(it)) },
                    )
                }
            }
        }
        item(key = "reading") {
            Section(title = stringResource(R.string.settings_section_reading)) {
                TextSizeSetting(scale = preferences.textScale, onCommit = { onAction(SettingsAction.SetTextScale(it)) })
                EditionSetting(
                    selected = preferences.preferredEdition,
                    onSelect = { onAction(SettingsAction.SetPreferredEdition(it)) },
                )
            }
        }
        item(key = "data") {
            Section(title = stringResource(R.string.settings_section_data)) {
                SwitchSetting(
                    title = stringResource(R.string.settings_save_history),
                    summary = stringResource(R.string.settings_save_history_summary),
                    checked = preferences.saveHistory,
                    onCheckedChange = { onAction(SettingsAction.SetSaveHistory(it)) },
                )
                ActionSetting(
                    title = stringResource(R.string.settings_clear_history),
                    summary = stringResource(R.string.settings_clear_history_summary),
                    onClick = { onAction(SettingsAction.RequestClear(ClearTarget.History)) },
                )
                ActionSetting(
                    title = stringResource(R.string.settings_clear_cache),
                    summary = stringResource(R.string.settings_clear_cache_summary),
                    onClick = { onAction(SettingsAction.RequestClear(ClearTarget.ArticleCache)) },
                )
            }
        }
        item(key = "about") {
            Section(title = stringResource(R.string.settings_section_about), showDivider = false) {
                ActionSetting(
                    title = stringResource(R.string.settings_about),
                    summary = stringResource(R.string.settings_about_summary),
                    onClick = { onAction(SettingsAction.OpenAbout) },
                    trailingIcon = WikiIcons.ChevronRight,
                )
            }
        }
    }
}

@Composable
private fun ThemeSetting(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    SegmentedSetting(
        title = stringResource(R.string.settings_theme),
        summary = null,
        options = ThemeOptions,
        selected = selected,
        label = { stringResource(it.label) },
        onSelect = onSelect,
    )
}

@Composable
private fun EditionSetting(
    selected: Edition,
    onSelect: (Edition) -> Unit,
) {
    SegmentedSetting(
        title = stringResource(R.string.settings_edition),
        summary = stringResource(R.string.settings_edition_summary),
        options = EditionOptions,
        selected = selected,
        label = { stringResource(it.label) },
        onSelect = onSelect,
    )
}

/**
 * The slider moves freely while dragged, with a preview line at the chosen size, and the value is saved once
 * the finger lifts, so the preference isn't written on every frame of the drag.
 */
@Composable
private fun TextSizeSetting(
    scale: Float,
    onCommit: (Float) -> Unit,
) {
    val slider = remember { SliderState(value = scale, steps = TEXT_SCALE_STEPS, trackRange = TextScaleRange) }
    // Follow the saved value when it changes from elsewhere, for example after a restore.
    LaunchedEffect(scale) { slider.value = scale }
    val currentOnCommit by rememberUpdatedState(onCommit)
    val dragged = slider.value
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_text_size),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.settings_text_size_value, (dragged * 100).roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            state = slider,
            onValueChange = { slider.value = it },
            onValueChangeFinished = { currentOnCommit(slider.value) },
            modifier = Modifier.testTag(TEXT_SIZE_SLIDER_TAG),
        )
        val body = MaterialTheme.typography.bodyLarge
        Text(
            text = stringResource(R.string.settings_text_size_preview),
            style = body.copy(fontSize = body.fontSize * dragged, lineHeight = body.lineHeight * dragged),
        )
    }
}

@Composable
private fun MessageSnackbar(
    message: SettingsMessage?,
    snackbarHostState: SnackbarHostState,
    onAction: (SettingsAction) -> Unit,
) {
    val currentOnAction by rememberUpdatedState(onAction)
    val text =
        message?.let {
            stringResource(
                when (it) {
                    SettingsMessage.HistoryCleared -> R.string.settings_history_cleared
                    SettingsMessage.CacheCleared -> R.string.settings_cache_cleared
                    SettingsMessage.ClearFailed -> R.string.settings_clear_failed
                },
            )
        }
    LaunchedEffect(message) {
        if (text == null) return@LaunchedEffect
        // Acknowledged only once it has been shown: clearing the message earlier would cancel this effect.
        snackbarHostState.showSnackbar(text)
        currentOnAction(SettingsAction.MessageShown)
    }
}

@Composable
private fun ClearDialog(
    target: ClearTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, message) =
        when (target) {
            ClearTarget.History -> {
                R.string.settings_clear_history_dialog_title to R.string.settings_clear_history_dialog_message
            }

            ClearTarget.ArticleCache -> {
                R.string.settings_clear_cache_dialog_title to R.string.settings_clear_cache_dialog_message
            }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_clear_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_clear_cancel)) } },
    )
}

private val ThemeOptions = ThemeMode.entries.toImmutableList()

private val EditionOptions = Edition.entries.toImmutableList()

private val ThemeMode.label: Int
    get() =
        when (this) {
            ThemeMode.System -> R.string.settings_theme_system
            ThemeMode.Light -> R.string.settings_theme_light
            ThemeMode.Dark -> R.string.settings_theme_dark
        }

private val Edition.label: Int
    get() =
        when (this) {
            Edition.Java -> R.string.settings_edition_java
            Edition.Bedrock -> R.string.settings_edition_bedrock
        }

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    WikiDroidTheme(dynamicColor = false) {
        SettingsScreen(
            state = SettingsState(preferences = UserPreferences()),
            onAction = {},
            dynamicColorSupported = true,
        )
    }
}
