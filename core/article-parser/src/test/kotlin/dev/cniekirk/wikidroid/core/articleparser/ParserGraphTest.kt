package dev.cniekirk.wikidroid.core.articleparser

import com.google.common.truth.Truth.assertThat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import org.junit.Test

/** Resolves the parser through Metro the way `:app` will, so the `@ContributesBinding` is covered. */
@DependencyGraph(AppScope::class)
interface ParserTestGraph {
    val parser: ArticleParser
}

class ParserGraphTest {
    @Test
    fun `the contributed binding resolves to the jsoup parser`() {
        val parser = createGraph<ParserTestGraph>().parser

        assertThat(parser).isInstanceOf(JsoupArticleParser::class.java)
        assertThat(parser.parse("<p>Hi</p>").single().blocks).hasSize(1)
    }
}
