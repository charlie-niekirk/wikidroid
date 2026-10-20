package dev.cniekirk.wikidroid.feature.tools.calculator

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIconButton
import dev.cniekirk.wikidroid.core.designsystem.component.WikiTopAppBar
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.recipe.CraftingPlan
import dev.cniekirk.wikidroid.core.model.recipe.ItemCount
import dev.cniekirk.wikidroid.core.model.recipe.RawMaterial
import dev.cniekirk.wikidroid.core.model.recipe.RecipeItem
import dev.cniekirk.wikidroid.core.ui.EmptyState
import dev.cniekirk.wikidroid.core.ui.ErrorState
import dev.cniekirk.wikidroid.core.ui.LoadingState
import dev.cniekirk.wikidroid.feature.tools.R
import kotlinx.collections.immutable.persistentListOf
import dev.cniekirk.wikidroid.core.ui.R as UiR

internal const val CALCULATOR_LIST_TAG = "calculator-list"

/** The resource calculator. Stateless: the route owns the ViewModel and navigation. */
@Composable
fun ResourceCalculatorScreen(
    state: CalculatorState,
    onAction: (CalculatorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            WikiTopAppBar(
                title = stringResource(R.string.calculator_title),
                onNavigateBack = { onAction(CalculatorAction.Back) },
                navigateBackDescription = stringResource(UiR.string.ui_navigate_back),
                actions = {
                    if (!state.choices.isEmpty) {
                        WikiIconButton(
                            icon = WikiIcons.Refresh,
                            contentDescription = stringResource(R.string.calculator_reset),
                            onClick = { onAction(CalculatorAction.Choice.Reset) },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.minecraftVersion != null) {
                val label = stringResource(R.string.calculator_add_item)
                ExtendedFloatingActionButton(
                    // The button's merged node does not carry its label, so screen readers would announce nothing.
                    modifier = Modifier.semantics { contentDescription = label },
                    text = { Text(label) },
                    icon = { WikiIcon(icon = WikiIcons.Add, contentDescription = null) },
                    onClick = { onAction(CalculatorAction.Sheet.OpenPicker) },
                )
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when {
            state.loadError != null -> {
                ErrorState(
                    error = state.loadError,
                    modifier = contentModifier,
                    onRetry = { onAction(CalculatorAction.Retry) },
                )
            }

            state.minecraftVersion == null -> {
                LoadingState(modifier = contentModifier)
            }

            state.targets.isEmpty() -> {
                EmptyState(
                    title = stringResource(R.string.calculator_empty_title),
                    message = stringResource(R.string.calculator_empty_message),
                    icon = WikiIcons.Calculate,
                    modifier = contentModifier,
                )
            }

            else -> {
                CalculatorList(state = state, onAction = onAction, contentPadding = padding)
            }
        }
    }

    CalculatorSheets(state = state, onAction = onAction)
}

@Composable
private fun CalculatorList(
    state: CalculatorState,
    onAction: (CalculatorAction) -> Unit,
    contentPadding: PaddingValues,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth().testTag(CALCULATOR_LIST_TAG),
            contentPadding =
                PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + FAB_CLEARANCE,
                ),
        ) {
            section(R.string.calculator_section_targets)
            state.targets.forEach { target ->
                item(key = "target-${target.item.id}") {
                    TargetRow(target = target, onAction = onAction)
                }
            }
            planItems(plan = state.plan, onAction = onAction)
            if (state.planError != null) {
                item(key = "plan-error") { Note(stringResource(R.string.calculator_plan_failed)) }
            }
            item(key = "version") {
                Note(stringResource(R.string.calculator_version, state.minecraftVersion.orEmpty()))
            }
        }
    }
}

private fun LazyListScope.planItems(
    plan: CraftingPlan,
    onAction: (CalculatorAction) -> Unit,
) {
    if (plan.raw.isNotEmpty()) {
        section(R.string.calculator_section_raw)
        plan.raw.forEach { material ->
            item(key = "raw-${material.item.id}") {
                MaterialRow(
                    material = material,
                    onClick = { onAction(CalculatorAction.Sheet.ShowItem(material.item.id)) },
                )
            }
        }
    }
    if (plan.steps.isNotEmpty()) {
        section(R.string.calculator_section_steps)
        if (plan.ignoredChoices.isNotEmpty()) {
            item(key = "ignored") { Note(stringResource(R.string.calculator_ignored)) }
        }
        itemsIndexed(plan.steps, key = { _, step -> "step-${step.output.item.id}" }) { index, step ->
            StepCard(number = index + 1, step = step, onAction = onAction)
        }
    }
    if (plan.leftovers.isNotEmpty()) {
        section(R.string.calculator_section_leftovers)
        plan.leftovers.forEach { leftover ->
            item(key = "left-${leftover.item.id}") {
                CountRow(item = leftover.item, count = leftover.count)
            }
        }
    }
}

private fun LazyListScope.section(title: Int) {
    item(key = "section-$title") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private val MAX_CONTENT_WIDTH = 640.dp

// Room for the last row to scroll clear of the floating button.
private val FAB_CLEARANCE = 88.dp

@Preview
@Composable
private fun ResourceCalculatorScreenPreview() {
    val glass = RecipeItem("glass", "Glass")
    val beacon = RecipeItem("beacon", "Beacon")
    WikiDroidTheme(dynamicColor = false) {
        ResourceCalculatorScreen(
            state =
                CalculatorState(
                    minecraftVersion = "26.3",
                    targets = persistentListOf(CalculatorTarget(beacon, 2)),
                    plan =
                        CraftingPlan(
                            raw = persistentListOf(RawMaterial(glass, 10, owned = false, craftable = false)),
                            leftovers = persistentListOf(ItemCount(beacon, 1)),
                        ),
                ),
            onAction = {},
        )
    }
}
