package dev.cniekirk.wikidroid.core.articleparser

import org.jsoup.nodes.Element

private val HIDDEN_TAGS = setOf("style", "script", "link", "meta", "noscript", "template")
private val HIDDEN_CLASSES = setOf("sprite-file", "hidden-alt-text", "mw-editsection", "indicator", "msgbox-icon")

/** Elements that carry no readable content: markup the browser would not show. */
internal fun Element.isHidden(): Boolean =
    normalName() in HIDDEN_TAGS ||
        HIDDEN_CLASSES.any(::hasClass) ||
        hasAttr("hidden") ||
        attr("style").replace(" ", "").contains("display:none")

/** The `<span typeof="mw:File">` (or `<figure>`) MediaWiki wraps around every embedded image. */
internal fun Element.isFileWrapper(): Boolean = attr("typeof").startsWith("mw:File")

internal fun Element.intAttr(name: String): Int? = attr(name).toIntOrNull()?.takeIf { it > 0 }
