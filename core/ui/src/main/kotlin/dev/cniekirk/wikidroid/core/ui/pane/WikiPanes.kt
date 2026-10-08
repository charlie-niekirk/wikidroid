package dev.cniekirk.wikidroid.core.ui.pane

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.ui.res.stringResource
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.ui.EmptyState
import dev.cniekirk.wikidroid.core.ui.R

/**
 * Entry metadata that tells the app's `ListDetailSceneStrategy` which pane a destination belongs in.
 * On a phone every destination is full-screen; on a wide window a [list] destination and the [detail]
 * destination above it show side by side.
 *
 * ```
 * entry<CategoryKey>(metadata = WikiPanes.list()) { ... }
 * entry<ArticleKey>(metadata = WikiPanes.detail()) { ... }
 * ```
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
object WikiPanes {
    /** Pages that open articles: Explore, a category, search. Shows "Select a page" until one is open. */
    fun list(): Map<String, Any> =
        ListDetailSceneStrategy.listPane(
            detailPlaceholder = {
                EmptyState(
                    title = stringResource(R.string.ui_select_page),
                    icon = WikiIcons.Explore,
                )
            },
        )

    /** The page opened from a [list] destination. */
    fun detail(): Map<String, Any> = ListDetailSceneStrategy.detailPane()
}
