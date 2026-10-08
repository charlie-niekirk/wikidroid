package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.Link
import dev.cniekirk.wikidroid.core.model.RichSpan
import dev.cniekirk.wikidroid.core.model.RichText
import dev.cniekirk.wikidroid.core.model.TextStyleFlag
import kotlinx.collections.immutable.toImmutableList

/**
 * Accumulates inline text the way a browser would lay it out: runs of whitespace collapse to one space,
 * `<br>` becomes a newline, and neighbouring runs with the same style and link merge into one span.
 */
internal class RichTextBuilder {
    private class Run(
        val text: StringBuilder,
        val styles: Set<TextStyleFlag>,
        val link: Link?,
    )

    private val runs = mutableListOf<Run>()
    private var last: Char = NEWLINE // Start of text behaves like start of a line.
    private var beforeLast: Char = NEWLINE

    fun append(
        text: String,
        styles: Set<TextStyleFlag> = emptySet(),
        link: Link? = null,
    ) {
        var collapsed = WHITESPACE.replace(text, " ")
        if (last == ' ' || last == NEWLINE) collapsed = collapsed.removePrefix(" ")
        if (collapsed.isEmpty()) return
        val tail = runs.lastOrNull()
        if (tail != null && tail.styles == styles && tail.link == link) {
            tail.text.append(collapsed)
        } else {
            runs += Run(StringBuilder(collapsed), styles, link)
        }
        remember(collapsed)
    }

    /** Ends the current line. Repeated calls give at most one blank line, and leading breaks are ignored. */
    fun lineBreak() {
        if (runs.isEmpty() || (last == NEWLINE && beforeLast == NEWLINE)) return
        trimTrailingSpace()
        val tail = runs.last()
        if (tail.styles.isEmpty() && tail.link == null) {
            tail.text.append(NEWLINE)
        } else {
            runs += Run(StringBuilder().append(NEWLINE), emptySet(), null)
        }
        beforeLast = last
        last = NEWLINE
    }

    /** Starts a new line unless the text is already at the start of one. */
    fun blockBoundary() {
        if (runs.isNotEmpty() && last != NEWLINE) lineBreak()
    }

    fun build(): RichText {
        while (runs.isNotEmpty()) {
            val tail = runs.last()
            val trimmed = tail.text.trimEnd(' ', NEWLINE)
            if (trimmed.isEmpty()) {
                // Not removeLast(): on JDK 21 that resolves to a method Android 10 doesn't have.
                runs.removeAt(runs.lastIndex)
            } else {
                tail.text.setLength(trimmed.length)
                break
            }
        }
        return RichText(runs.map { RichSpan(it.text.toString(), it.styles, it.link) }.toImmutableList())
    }

    private fun trimTrailingSpace() {
        val tail = runs.last()
        if (tail.text.lastOrNull() == ' ') tail.text.setLength(tail.text.length - 1)
    }

    private fun remember(appended: String) {
        last = appended.last()
        beforeLast = if (appended.length > 1) appended[appended.length - 2] else last
    }

    private companion object {
        const val NEWLINE = '\n'
        val WHITESPACE = Regex("[ \\t\\r\\n\\u000c\\u00a0]+")
    }
}
