package dev.cniekirk.wikidroid.feature.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIcon
import dev.cniekirk.wikidroid.core.designsystem.component.WikiIconButton
import dev.cniekirk.wikidroid.core.designsystem.component.WikiTopAppBar
import dev.cniekirk.wikidroid.core.designsystem.icon.WikiIcons
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.Article
import dev.cniekirk.wikidroid.core.model.ArticleSection
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.model.RichText
import dev.cniekirk.wikidroid.core.ui.AttributionFooter
import dev.cniekirk.wikidroid.core.ui.ErrorState
import dev.cniekirk.wikidroid.core.ui.LoadingState
import dev.cniekirk.wikidroid.feature.article.render.ArticleBlock
import kotlinx.collections.immutable.persistentListOf
import dev.cniekirk.wikidroid.core.ui.R as UiR

internal const val ARTICLE_LIST_TAG = "article-list"

/** Widest the text column gets, so lines stay readable on a tablet shown full-width. */
private val ReadingWidth = 720.dp

/** An article: top bar actions, then its sections as a lazy list. Stateless: the route owns the ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    state: ArticleState,
    onAction: (ArticleAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            WikiTopAppBar(
                title = state.article?.displayTitle ?: state.title,
                onNavigateBack = { onAction(ArticleAction.Back) },
                navigateBackDescription = stringResource(UiR.string.ui_navigate_back),
                scrollBehavior = scrollBehavior,
                actions = { if (state.article != null) ArticleActions(state, onAction) },
            )
        },
    ) { padding ->
        when (val phase = state.phase) {
            ArticlePhase.Loading -> {
                LoadingState(modifier = Modifier.padding(padding))
            }

            is ArticlePhase.Failed -> {
                ErrorState(
                    error = phase.error,
                    onRetry = { onAction(ArticleAction.Retry) },
                    modifier = Modifier.padding(padding),
                )
            }

            is ArticlePhase.Loaded -> {
                // Only the article's text follows the reader's size setting; the chrome keeps the system size.
                val density = LocalDensity.current
                val scaled =
                    remember(density, state.textScale) {
                        Density(
                            density.density,
                            density.fontScale * state.textScale,
                        )
                    }
                CompositionLocalProvider(LocalDensity provides scaled) {
                    ArticleContent(
                        article = phase.article,
                        state = state,
                        onAction = onAction,
                        contentPadding = padding,
                    )
                }
                if (state.isTocVisible) {
                    TableOfContentsSheet(article = phase.article, onAction = onAction)
                }
            }
        }
    }
}

@Composable
private fun ArticleActions(
    state: ArticleState,
    onAction: (ArticleAction) -> Unit,
) {
    val article = state.article ?: return
    if (article.tableOfContents.isNotEmpty()) {
        WikiIconButton(
            icon = WikiIcons.Toc,
            contentDescription = stringResource(R.string.article_toc_action),
            onClick = { onAction(ArticleAction.ShowToc) },
        )
    }
    WikiIconButton(
        icon = if (state.isBookmarked) WikiIcons.Bookmark else WikiIcons.BookmarkBorder,
        contentDescription =
            stringResource(
                if (state.isBookmarked) R.string.article_bookmark_remove else R.string.article_bookmark_add,
            ),
        onClick = { onAction(ArticleAction.ToggleBookmark) },
    )
    if (article.pageUrl != null) {
        WikiIconButton(
            icon = WikiIcons.Share,
            contentDescription = stringResource(R.string.article_share),
            onClick = { onAction(ArticleAction.Share) },
        )
        OverflowMenu(onOpenOnWiki = { onAction(ArticleAction.OpenOnWiki) })
    }
}

@Composable
private fun OverflowMenu(onOpenOnWiki: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        WikiIconButton(
            icon = WikiIcons.MoreVert,
            contentDescription = stringResource(R.string.article_more_options),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.article_open_on_wiki)) },
                leadingIcon = { WikiIcon(icon = WikiIcons.OpenInNew, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenOnWiki()
                },
            )
        }
    }
}

@Composable
private fun ArticleContent(
    article: Article,
    state: ArticleState,
    onAction: (ArticleAction) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    val layout = remember(article, state.collapsedSections) { buildArticleLayout(article, state.collapsedSections) }

    // The section holding the anchor was unfolded in the same state change, so the row exists by now.
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(state.pendingAnchor, layout) {
        val anchor = state.pendingAnchor ?: return@LaunchedEffect
        layout.indexOfAnchor(anchor)?.let { listState.animateScrollToItem(it) }
        currentOnAction(ArticleAction.AnchorHandled)
    }

    val onLinkClick = remember(onAction) { { link: Link -> onAction(ArticleAction.OpenLink(link)) } }
    val onOpenOnWiki = remember(onAction) { { onAction(ArticleAction.OpenOnWiki) } }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = ReadingWidth).fillMaxSize().testTag(ARTICLE_LIST_TAG),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(layout.items, key = { it.key }, contentType = { it::class }) { item ->
                when (item) {
                    is ArticleListItem.SectionHeader -> {
                        SectionHeader(
                            item = item,
                            onToggle = { onAction(ArticleAction.ToggleSection(item.sectionIndex)) },
                        )
                    }

                    is ArticleListItem.Block -> {
                        ArticleBlock(block = item.block, onLinkClick = onLinkClick, onOpenOnWiki = onOpenOnWiki)
                    }

                    ArticleListItem.Footer -> {
                        AttributionFooter(
                            onOpenUrl = { onAction(ArticleAction.OpenUrl(it)) },
                            pageUrl = article.pageUrl,
                        )
                    }
                }
            }
        }
    }
}

/** A section's title, which folds and unfolds it. */
@Composable
private fun SectionHeader(
    item: ArticleListItem.SectionHeader,
    onToggle: () -> Unit,
) {
    val clickLabel =
        stringResource(if (item.isCollapsed) R.string.article_section_expand else R.string.article_section_collapse)
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onToggle)
                    .padding(vertical = 8.dp)
                    .semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.heading.text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
            )
            WikiIcon(
                icon = if (item.isCollapsed) WikiIcons.ExpandMore else WikiIcons.ExpandLess,
                contentDescription = null,
            )
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableOfContentsSheet(
    article: Article,
    onAction: (ArticleAction) -> Unit,
) {
    val entries = remember(article) { article.tableOfContents }
    ModalBottomSheet(onDismissRequest = { onAction(ArticleAction.HideToc) }) {
        Text(
            text = stringResource(R.string.article_toc_title),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        LazyColumn(modifier = Modifier.testTag(TOC_LIST_TAG), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(entries) { heading -> TocEntry(heading, onAction) }
        }
    }
}

internal const val TOC_LIST_TAG = "toc-list"

@Composable
private fun TocEntry(
    heading: ContentBlock.Heading,
    onAction: (ArticleAction) -> Unit,
) {
    // Level 2 is the top of the outline; each deeper level indents one step.
    val indent = ((heading.level - 2).coerceAtLeast(0) * 16).dp
    Text(
        text = heading.text,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onAction(ArticleAction.GoToAnchor(heading.anchor)) }
                .padding(start = 24.dp + indent, end = 24.dp, top = 12.dp, bottom = 12.dp),
        style = if (heading.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
    )
}

@PreviewLightDark
@Composable
private fun ArticleScreenPreview() {
    WikiDroidTheme(dynamicColor = false) {
        ArticleScreen(
            state =
                ArticleState(
                    title = "Diamond",
                    phase = ArticlePhase.Loaded(previewArticle()),
                ),
            onAction = {},
        )
    }
}

private fun previewArticle() =
    Article(
        title = "Diamond",
        displayTitle = "Diamond",
        revisionId = 1,
        sections =
            persistentListOf(
                ArticleSection(
                    heading = null,
                    blocks =
                        persistentListOf(
                            ContentBlock.Paragraph(
                                RichText.of("Diamond is a rare mineral."),
                            ),
                        ),
                ),
            ),
        pageUrl = "https://minecraft.wiki/w/Diamond",
    )
