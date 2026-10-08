package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.ArticleSection
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.ImmutableList
import org.jsoup.Jsoup

@Inject
@ContributesBinding(AppScope::class)
class JsoupArticleParser : ArticleParser {
    override fun parse(html: String): ImmutableList<ArticleSection> {
        val root = Jsoup.parseBodyFragment(html, WikiUrls.ORIGIN).body()
        ArticleCleaner.clean(root)
        val collector = SectionCollector()
        BlockParser.parseChildren(root, collector)
        return collector.finish()
    }
}
