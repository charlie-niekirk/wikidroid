package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.ArticleSection
import kotlinx.collections.immutable.ImmutableList

/** Turns the HTML of a MediaWiki `action=parse` response into the structured blocks the UI renders. */
fun interface ArticleParser {
    /**
     * Parses [html] (the `parse.text` field, mobile format or not) into sections.
     *
     * The lead section has a `null` heading; each `<h2>` starts a new section and deeper headings stay in the
     * section's blocks. Never throws on odd markup: anything it can't map becomes `ContentBlock.Unsupported`.
     */
    fun parse(html: String): ImmutableList<ArticleSection>
}
