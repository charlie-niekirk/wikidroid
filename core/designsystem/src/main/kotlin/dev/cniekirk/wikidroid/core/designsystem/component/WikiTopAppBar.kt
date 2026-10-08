package dev.cniekirk.wikidroid.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme

/**
 * The app's top bar. Pass [onNavigateBack] and [navigateBackDescription] to show a back arrow.
 * [navigateBackDescription] is only used when [onNavigateBack] is set.
 */
@Composable
fun WikiTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    navigateBackDescription: String = "",
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = {
            if (onNavigateBack != null) {
                WikiIconButton(
                    icon = WikiIcons.ArrowBack,
                    contentDescription = navigateBackDescription,
                    onClick = onNavigateBack,
                )
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

@Preview
@Composable
private fun WikiTopAppBarPreview() {
    WikiDroidTheme(dynamicColor = false) {
        WikiTopAppBar(
            title = "Diamond",
            onNavigateBack = {},
            navigateBackDescription = "Back",
            actions = {
                WikiIconButton(icon = WikiIcons.Share, contentDescription = "Share", onClick = {})
            },
        )
    }
}
