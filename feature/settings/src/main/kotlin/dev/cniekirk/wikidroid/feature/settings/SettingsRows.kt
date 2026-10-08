package dev.cniekirk.wikidroid.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import kotlinx.collections.immutable.ImmutableList

/** Keeps a list row's tinted background off the screen edge. */
private val RowInset = 8.dp

@Composable
internal fun Section(
    title: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        content()
        if (showDivider) HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

/** A title with a row of mutually exclusive choices beneath it. */
@Composable
internal fun <T> SegmentedSetting(
    title: String,
    summary: String?,
    options: ImmutableList<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(text = label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun SwitchSetting(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.padding(horizontal = RowInset),
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) {
        Text(title)
    }
}

@Composable
internal fun ActionSetting(
    title: String,
    summary: String,
    onClick: () -> Unit,
    @DrawableRes trailingIcon: Int? = null,
) {
    ListItem(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = RowInset),
        supportingContent = { Text(summary) },
        trailingContent = trailingIcon?.let { icon -> { WikiIcon(icon = icon, contentDescription = null) } },
    ) {
        Text(title)
    }
}
