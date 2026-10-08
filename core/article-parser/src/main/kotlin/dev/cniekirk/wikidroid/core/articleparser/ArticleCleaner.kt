package dev.cniekirk.wikidroid.core.articleparser

import org.jsoup.nodes.Element

/** Removes everything that is chrome rather than article content, before any block is built. */
internal object ArticleCleaner {
    private val STRIPPED =
        listOf(
            "table.navbox",
            ".navigation-not-searchable",
            "pre.history-json",
            ".chest-json",
            ".noexcerpt",
            ".navbar-mini",
            "#toc",
            ".mw-editsection",
            ".mw-cite-backlink",
            ".mw-collapsible-toggle",
            "style",
            "script",
            "noscript",
            "link",
            "meta",
        ).joinToString()

    fun clean(root: Element) {
        removeNavigation(root)
        root.select(STRIPPED).remove()
    }

    /** The "Navigation" heading marks the start of the navbox footer; it and everything after it is dropped. */
    private fun removeNavigation(root: Element) {
        val h2 = root.selectFirst("h2#Navigation") ?: return
        val heading = h2.parent()?.takeIf { it !== root && it.hasClass("mw-heading") } ?: h2
        var current: Element = heading
        while (current !== root) {
            val parent = current.parent() ?: break
            current.nextElementSiblings().remove()
            current = parent
        }
        heading.remove()
    }
}
