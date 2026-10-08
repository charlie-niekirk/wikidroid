package dev.cniekirk.wikidroid.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.cniekirk.wikidroid.R
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.navigation.AboutKey
import dev.cniekirk.wikidroid.core.navigation.ArticleKey
import dev.cniekirk.wikidroid.core.navigation.CategoryKey
import dev.cniekirk.wikidroid.core.navigation.ExploreKey
import dev.cniekirk.wikidroid.core.navigation.TopLevelKey
import dev.cniekirk.wikidroid.core.navigation.WikiKey
import dev.cniekirk.wikidroid.core.ui.MessageState

internal const val PLACEHOLDER_TAG = "placeholder"

/**
 * Stands in for any destination whose feature module has not registered an entry yet. The feature sessions
 * replace these one by one just by contributing an `EntryProviderInstaller`; nothing here needs to change.
 */
@Composable
internal fun PlaceholderScreen(
    key: WikiKey,
    modifier: Modifier = Modifier,
) {
    val title =
        when (key) {
            is TopLevelKey -> stringResource(key.labelRes)
            is CategoryKey -> stringResource(R.string.placeholder_category_title, key.title)
            AboutKey -> stringResource(R.string.placeholder_about_title)
            is ArticleKey -> stringResource(R.string.placeholder_article_title, key.title)
        }
    Surface(modifier = modifier.fillMaxSize().testTag(PLACEHOLDER_TAG)) {
        MessageState(
            icon = if (key is TopLevelKey) key.iconRes else WikiIcons.Explore,
            title = title,
            message = stringResource(R.string.placeholder_coming_soon),
        )
    }
}

@Preview
@Composable
private fun PlaceholderScreenPreview() {
    WikiDroidTheme(dynamicColor = false) {
        PlaceholderScreen(key = ExploreKey)
    }
}
