package dev.cniekirk.wikidroid.feature.tools.calculator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.model.recipe.RecipeItem
import dev.cniekirk.wikidroid.core.model.recipe.RecipeOption
import dev.cniekirk.wikidroid.feature.tools.R
import kotlinx.collections.immutable.ImmutableList

internal const val PICKER_FIELD_TAG = "calculator-picker-field"
internal const val PICKER_LIST_TAG = "calculator-picker-list"
internal const val ITEM_SHEET_TAG = "calculator-item-sheet"
internal const val VARIANT_LIST_TAG = "calculator-variant-list"

internal fun pickerItemTag(itemId: String) = "calculator-picker-$itemId"

internal fun recipeOptionTag(recipeId: String) = "calculator-recipe-$recipeId"

internal fun variantTag(itemId: String) = "calculator-variant-$itemId"

/** Whichever sheet [CalculatorState.sheet] asks for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalculatorSheets(
    state: CalculatorState,
    onAction: (CalculatorAction) -> Unit,
) {
    val dismiss = { onAction(CalculatorAction.Sheet.Dismiss) }
    when (val sheet = state.sheet) {
        CalculatorSheet.Picker -> {
            ModalBottomSheet(
                onDismissRequest = dismiss,
            ) { PickerSheet(results = state.searchResults, onAction = onAction) }
        }

        is CalculatorSheet.Item -> {
            ModalBottomSheet(
                onDismissRequest = dismiss,
            ) { ItemSheet(state = state, itemId = sheet.itemId, onAction = onAction) }
        }

        is CalculatorSheet.Variants -> {
            ModalBottomSheet(onDismissRequest = dismiss) { VariantSheet(sheet = sheet, onAction = onAction) }
        }

        null -> {
            Unit
        }
    }
}

/** Search the craftable items; the query text lives here and goes to the ViewModel as it changes. */
@Composable
private fun PickerSheet(
    results: ImmutableList<RecipeItem>?,
    onAction: (CalculatorAction) -> Unit,
) {
    val query = rememberTextFieldState()
    val send by rememberUpdatedState(onAction)
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString() }.collect { send(CalculatorAction.QueryChanged(it)) }
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            state = query,
            modifier = Modifier.fillMaxWidth().testTag(PICKER_FIELD_TAG),
            placeholder = { Text(stringResource(R.string.calculator_search)) },
            leadingIcon = { WikiIcon(icon = WikiIcons.Search, contentDescription = null) },
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        if (results != null && results.isEmpty()) {
            Text(
                stringResource(R.string.calculator_no_results),
                modifier = Modifier.padding(vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(modifier = Modifier.testTag(PICKER_LIST_TAG)) {
            items(results.orEmpty(), key = { it.id }) { item ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(pickerItemTag(item.id))
                            .clickable { onAction(CalculatorAction.Target.Add(item)) }
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ItemIcon(item)
                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** Stop at an item or craft it, pick its recipe, or read about it. */
@Composable
private fun ItemSheet(
    state: CalculatorState,
    itemId: String,
    onAction: (CalculatorAction) -> Unit,
) {
    val step = state.stepFor(itemId)
    val raw = state.rawFor(itemId)
    val item = step?.output?.item ?: raw?.item ?: return
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .testTag(ITEM_SHEET_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ItemIcon(item, size = 48.dp)
            Text(item.name, style = MaterialTheme.typography.titleLarge)
        }
        when {
            step != null -> {
                OutlinedButton(onClick = { onAction(CalculatorAction.Choice.Own(itemId)) }) {
                    Text(stringResource(R.string.calculator_i_have_this))
                }
            }

            raw != null && raw.owned -> {
                Button(onClick = { onAction(CalculatorAction.Choice.Craft(itemId)) }) {
                    Text(stringResource(R.string.calculator_break_down))
                }
            }

            raw != null && raw.craftable -> {
                OutlinedButton(onClick = { onAction(CalculatorAction.Choice.Craft(itemId)) }) {
                    Text(stringResource(R.string.calculator_craft_instead))
                }
            }
        }
        if (step != null && step.options.size > 1) {
            Text(stringResource(R.string.calculator_recipes), style = MaterialTheme.typography.titleSmall)
            step.options.forEach { option -> RecipeOptionRow(itemId = itemId, option = option, onAction = onAction) }
        }
        TextButton(onClick = { onAction(CalculatorAction.OpenArticle(item.name)) }) {
            Text(stringResource(R.string.calculator_open_in_wiki))
        }
    }
}

@Composable
private fun RecipeOptionRow(
    itemId: String,
    option: RecipeOption,
    onAction: (CalculatorAction) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(recipeOptionTag(option.recipeId))
                .clickable(
                    role = Role.RadioButton,
                ) { onAction(CalculatorAction.Choice.ChooseRecipe(itemId, option.recipeId)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = option.chosen, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                stringResource(R.string.calculator_recipe_makes, option.makes),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                option.inputs.joinToString { "${formatCount(it.count)} ${it.item.name}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VariantSheet(
    sheet: CalculatorSheet.Variants,
    onAction: (CalculatorAction) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.calculator_variants_title), style = MaterialTheme.typography.titleLarge)
        LazyColumn(modifier = Modifier.testTag(VARIANT_LIST_TAG)) {
            items(sheet.options, key = { it.id }) { item ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(variantTag(item.id))
                            .clickable(role = Role.RadioButton) {
                                onAction(CalculatorAction.Choice.ChooseVariant(sheet.key, item.id))
                            }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = item.id == sheet.selected, onClick = null)
                    ItemIcon(item)
                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
