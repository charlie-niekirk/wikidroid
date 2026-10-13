package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import dev.cniekirk.wikidroid.core.domain.seedmap.FindNearestStructureUseCase
import dev.cniekirk.wikidroid.core.model.PointOfInterest
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.seedmap.StructureWikiTitles

// The seed map's snackbars. Each one is shown from a state field and acknowledged once showSnackbar returns:
// acknowledging first would change the effect's key and cancel it.

/** Says that no [missing] structure was within reach, then acknowledges it once the snackbar has gone. */
@Composable
internal fun NearestMissingSnackbar(
    missing: StructureType?,
    snackbarHostState: SnackbarHostState,
    onAction: (SeedMapAction) -> Unit,
) {
    val text =
        missing?.let {
            stringResource(
                R.string.seedmap_nearest_missing,
                StructureWikiTitles.titleFor(it),
                "%,d".format(FindNearestStructureUseCase.REACH_BLOCKS),
            )
        }
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(missing) {
        if (text == null) return@LaunchedEffect
        // Acknowledged only once it has been shown: clearing it earlier would cancel this effect.
        snackbarHostState.showSnackbar(text)
        currentOnAction(SeedMapAction.NearestMissingShown)
    }
}

/** "Deleted Base" with Undo, then acknowledges it (undone or not) once the snackbar has gone. */
@Composable
internal fun RemovedPoiSnackbar(
    removed: PointOfInterest?,
    snackbarHostState: SnackbarHostState,
    onAction: (SeedMapAction) -> Unit,
) {
    val text = removed?.let { stringResource(R.string.seedmap_poi_deleted, it.displayName()) }
    val undo = stringResource(R.string.seedmap_undo)
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(removed) {
        if (text == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(text, actionLabel = undo, duration = SnackbarDuration.Short)
        currentOnAction(
            if (result ==
                SnackbarResult.ActionPerformed
            ) {
                SeedMapAction.UndoDeletePoi
            } else {
                SeedMapAction.RemovedPoiShown
            },
        )
    }
}

/** Says which dimension a linked portal went to, then acknowledges it. */
@Composable
internal fun LinkedPortalSnackbar(
    addedTo: Dimension?,
    snackbarHostState: SnackbarHostState,
    onAction: (SeedMapAction) -> Unit,
) {
    val text = addedTo?.let { stringResource(R.string.seedmap_poi_linked_added, stringResource(it.labelRes())) }
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(addedTo) {
        if (text == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        currentOnAction(SeedMapAction.LinkedPortalShown)
    }
}
