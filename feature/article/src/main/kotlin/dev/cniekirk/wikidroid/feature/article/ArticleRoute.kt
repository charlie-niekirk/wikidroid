package dev.cniekirk.wikidroid.feature.article

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.ui.openWebUrl
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import org.orbitmvi.orbit.compose.collectAsState

/**
 * Connects [ArticleScreen] to a ViewModel created for [title] and to the rest of the app.
 * Links to other articles go to [onOpenArticle]; external ones open in a Custom Tab.
 */
@Composable
fun ArticleRoute(
    title: String,
    anchor: String?,
    onOpenArticle: (title: String, anchor: String?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        assistedMetroViewModel<ArticleViewModel, ArticleViewModel.Factory> { create(title, anchor) }
    val state by viewModel.collectAsState()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.article_share_chooser)

    ArticleScreen(
        state = state,
        onAction = { action ->
            val article = state.article
            when (action) {
                is ArticleAction.OpenLink -> {
                    when (val link = action.link) {
                        is Link.External -> {
                            context.openWebUrl(link.url)
                        }

                        is Link.Internal -> {
                            val here = article?.title ?: state.title
                            val target = link.anchor
                            if (target != null && link.title.isSameTitleAs(here)) {
                                viewModel.onAction(ArticleAction.GoToAnchor(target))
                            } else {
                                onOpenArticle(link.title, link.anchor)
                            }
                        }
                    }
                }

                is ArticleAction.OpenUrl -> {
                    context.openWebUrl(action.url)
                }

                ArticleAction.OpenOnWiki -> {
                    article?.pageUrl?.let(context::openWebUrl)
                }

                ArticleAction.Share -> {
                    article?.pageUrl?.let { context.shareArticle(article.displayTitle, it, chooserTitle) }
                }

                ArticleAction.Back -> {
                    onNavigateBack()
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
        modifier = modifier,
    )
}

private fun String.isSameTitleAs(other: String): Boolean =
    trim().replace('_', ' ').equals(other.trim().replace('_', ' '), ignoreCase = true)
