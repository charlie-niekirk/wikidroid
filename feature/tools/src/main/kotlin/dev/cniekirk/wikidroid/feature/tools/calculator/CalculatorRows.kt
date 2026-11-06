package dev.cniekirk.wikidroid.feature.tools.calculator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIconButton
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.model.recipe.CraftingStep
import dev.cniekirk.wikidroid.core.model.recipe.RawMaterial
import dev.cniekirk.wikidroid.core.model.recipe.RecipeItem
import dev.cniekirk.wikidroid.core.model.recipe.StepInput
import dev.cniekirk.wikidroid.core.ui.PageThumbnail
import dev.cniekirk.wikidroid.feature.tools.R
import java.text.NumberFormat

internal fun quantityTag(itemId: String) = "calculator-quantity-$itemId"

/** An item's inventory sprite from the wiki, drawn pixel-sharp. */
@Composable
internal fun ItemIcon(
    item: RecipeItem,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    PageThumbnail(url = item.iconUrl, modifier = modifier, size = size, pixelated = true)
}

internal fun formatCount(count: Long): String = NumberFormat.getIntegerInstance().format(count)

/**
 * One thing to make, with a quantity field and steppers. The field's text lives here, not in the state, so typing is
 * never overwritten by a round trip; each whole number typed is sent as [CalculatorAction.Target.SetQuantity].
 */
@Composable
internal fun TargetRow(
    target: CalculatorTarget,
    onAction: (CalculatorAction) -> Unit,
) {
    val id = target.item.id
    val field = rememberTextFieldState(target.quantity.toString())
    val quantity by rememberUpdatedState(target.quantity)
    val send by rememberUpdatedState(onAction)
    LaunchedEffect(target.quantity) {
        if (field.text.toString().toIntOrNull() !=
            target.quantity
        ) {
            field.setTextAndPlaceCursorAtEnd(target.quantity.toString())
        }
    }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString().toIntOrNull() }.collect { typed ->
            if (typed != null && typed != quantity) send(CalculatorAction.Target.SetQuantity(id, typed))
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ItemIcon(target.item)
        Text(target.item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        WikiIconButton(
            icon = WikiIcons.Remove,
            contentDescription = stringResource(R.string.calculator_decrease, target.item.name),
            onClick = { field.setTextAndPlaceCursorAtEnd(CalculatorState.clamp(target.quantity - 1).toString()) },
        )
        val description = stringResource(R.string.calculator_quantity, target.item.name)
        OutlinedTextField(
            state = field,
            modifier =
                Modifier.width(QUANTITY_WIDTH).testTag(quantityTag(id)).semantics {
                    contentDescription =
                        description
                },
            inputTransformation = DigitsOnly,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        WikiIconButton(
            icon = WikiIcons.Add,
            contentDescription = stringResource(R.string.calculator_increase, target.item.name),
            onClick = { field.setTextAndPlaceCursorAtEnd(CalculatorState.clamp(target.quantity + 1).toString()) },
        )
        WikiIconButton(
            icon = WikiIcons.Close,
            contentDescription = stringResource(R.string.calculator_remove, target.item.name),
            onClick = { onAction(CalculatorAction.Target.Remove(id)) },
        )
    }
}

private val DigitsOnly =
    InputTransformation
        .maxLength(CalculatorState.MAX_QUANTITY.toString().length)
        .then(InputTransformation.byValue { _, proposed -> proposed.filter(Char::isDigit) })

private val QUANTITY_WIDTH = 80.dp

/** A raw material: tap it to stop at it, craft it, or read about it. */
@Composable
internal fun MaterialRow(
    material: RawMaterial,
    onClick: () -> Unit,
) {
    CountRow(
        item = material.item,
        count = material.count,
        modifier = Modifier.clickable(onClick = onClick),
        supporting = if (material.owned) stringResource(R.string.calculator_owned) else null,
    )
}

@Composable
internal fun CountRow(
    item: RecipeItem,
    count: Long,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ItemIcon(item)
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            stringResource(R.string.calculator_count, formatCount(count)),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** One crafting step: how many crafts, what goes in (variants open a chooser) and what comes out. */
@Composable
internal fun StepCard(
    number: Int,
    step: CraftingStep,
    onAction: (CalculatorAction) -> Unit,
) {
    val output = step.output.item
    OutlinedCard(
        onClick = { onAction(CalculatorAction.Sheet.ShowItem(output.id)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ItemIcon(output)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.calculator_step, number, formatCount(step.crafts)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.calculator_step_makes, formatCount(step.output.count), output.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                step.inputs.forEach { input -> InputChip(input = input, onAction = onAction) }
            }
            StepFooter(step = step, onAction = onAction)
        }
    }
}

@Composable
private fun InputChip(
    input: StepInput,
    onAction: (CalculatorAction) -> Unit,
) {
    val key = input.variantKey
    AssistChip(
        onClick = {
            val action =
                if (key != null) {
                    CalculatorAction.Sheet.ShowVariants(key, input.options, input.item.id)
                } else {
                    CalculatorAction.Sheet.ShowItem(input.item.id)
                }
            onAction(action)
        },
        label = { Text("${formatCount(input.count)} ${input.item.name}") },
        leadingIcon = { ItemIcon(input.item, size = 18.dp) },
        trailingIcon =
            if (key !=
                null
            ) {
                ({ WikiIcon(icon = WikiIcons.ExpandMore, contentDescription = null) })
            } else {
                null
            },
    )
}

@Composable
private fun StepFooter(
    step: CraftingStep,
    onAction: (CalculatorAction) -> Unit,
) {
    val others = step.options.size - 1
    if (!step.needsCraftingTable && others <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (step.needsCraftingTable) {
            Text(
                stringResource(R.string.calculator_needs_table),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            Row(modifier = Modifier.weight(1f)) {}
        }
        if (others > 0) {
            TextButton(onClick = { onAction(CalculatorAction.Sheet.ShowItem(step.output.item.id)) }) {
                Text(stringResource(R.string.calculator_other_recipes, others))
            }
        }
    }
}
