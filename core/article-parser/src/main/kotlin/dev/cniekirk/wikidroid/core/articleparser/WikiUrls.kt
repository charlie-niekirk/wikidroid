package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.Link
import org.jsoup.nodes.Element
import java.net.URLDecoder

/** Resolves the relative URLs MediaWiki emits and classifies links as internal articles or external pages. */
internal object WikiUrls {
    const val ORIGIN = "https://minecraft.wiki"

    private const val ARTICLE_PREFIX = "$ORIGIN/w/"

    /** Namespaces whose pages the article screen can't show, so they open in the browser instead. */
    private val BROWSER_ONLY_NAMESPACES = setOf("file", "media", "special")

    fun absolute(url: String): String =
        when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> ORIGIN + url
            else -> url
        }

    /**
     * The link an `<a>` points to, or `null` when it should render as plain text: no `href`, a same-page
     * fragment (citation markers, mostly), or a red link to a page that doesn't exist.
     */
    fun link(anchor: Element): Link? {
        val href = anchor.attr("href").trim()
        val isRedLink = anchor.hasClass("new") || href.contains("redlink=1")
        return if (href.isEmpty() || href.startsWith("#") || isRedLink) null else classify(absolute(href))
    }

    private fun classify(url: String): Link {
        if (!url.startsWith(ARTICLE_PREFIX)) return Link.External(url)
        val target = url.removePrefix(ARTICLE_PREFIX)
        val path = target.substringBefore('#')
        val title = decode(path).replace('_', ' ').trim()
        val namespace = title.substringBefore(':', missingDelimiterValue = "").lowercase()
        return if ('?' in path || title.isEmpty() || namespace in BROWSER_ONLY_NAMESPACES) {
            Link.External(url)
        } else {
            Link.Internal(
                title,
                target.substringAfter('#', missingDelimiterValue = "").takeIf { it.isNotEmpty() }?.let(::decode),
            )
        }
    }

    // URLDecoder maps '+' to a space, which is a form-encoding rule that doesn't apply to wiki paths.
    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(value)
}
