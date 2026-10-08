package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.ArticleSection
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.model.RichText

/** HTML fixtures are trimmed `action=parse` responses captured from minecraft.wiki on 2026-10-08. */
internal fun fixture(name: String): String {
    val resource = "fixtures/$name.html"
    val stream =
        checkNotNull(ArticleParser::class.java.classLoader.getResourceAsStream(resource)) { "Missing $resource" }
    return stream.use { it.readBytes().decodeToString() }
}

private val parser = JsoupArticleParser()

internal fun parse(html: String): List<ArticleSection> = parser.parse(html)

/** The blocks of a fragment that has no headings, i.e. of its lead section. */
internal fun blocksOf(html: String): List<ContentBlock> = parse(html).flatMap { it.blocks }

internal inline fun <reified T : ContentBlock> blockOf(html: String): T = blocksOf(html).filterIsInstance<T>().single()

internal fun List<ArticleSection>.allBlocks(): List<ContentBlock> = flatMap { it.blocks }

internal fun List<ArticleSection>.section(title: String): ArticleSection = single { it.heading?.text == title }

internal val RichText.links: List<Link> get() = spans.mapNotNull { it.link }
