package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.InlineImage
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
        val image: InlineImage? = null,
    ) {
        /** Text joins the previous run when it looks the same; an image run never takes text. */
        fun canTakeText(
            styles: Set<TextStyleFlag>,
            link: Link?,
        ): Boolean = image == null && this.styles == styles && this.link == link
    }

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
        if (tail != null && tail.canTakeText(styles, link)) {
            tail.text.append(collapsed)
        } else {
            runs += Run(StringBuilder(collapsed), styles, link)
        }
        remember(collapsed)
    }

    /** A picture that sits in the line like a character. [alt] stays as the span's text. */
    fun appendImage(
        image: InlineImage,
        alt: String = "",
        styles: Set<TextStyleFlag> = emptySet(),
        link: Link? = null,
    ) {
        runs += Run(StringBuilder(alt), styles, link, image)
        // An image is content: the space that follows it is a real space, not the start of a line.
        beforeLast = last
        last = IMAGE
    }

    /** Ends the current line. Repeated calls give at most one blank line, and leading breaks are ignored. */
    fun lineBreak() {
        if (runs.isEmpty() || (last == NEWLINE && beforeLast == NEWLINE)) return
        trimTrailingSpace()
        val tail = runs.last()
        if (tail.image == null && tail.styles.isEmpty() && tail.link == null) {
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
        trimTrailingRuns()
        return RichText(runs.map { RichSpan(it.text.toString(), it.styles, it.link, it.image) }.toImmutableList())
    }

    /** Drops trailing whitespace, and runs that become empty. An image is content, so trimming stops at one. */
    private fun trimTrailingRuns() {
        var done = false
        while (!done && runs.isNotEmpty()) {
            val tail = runs.last()
            val trimmed = if (tail.image == null) tail.text.trimEnd(' ', NEWLINE) else null
            when {
                trimmed == null -> {
                    done = true
                }

                trimmed.isEmpty() -> {
                    // Not removeLast(): on JDK 21 that resolves to a method Android 10 doesn't have.
                    runs.removeAt(runs.lastIndex)
                }

                else -> {
                    tail.text.setLength(trimmed.length)
                    done = true
                }
            }
        }
    }

    private fun trimTrailingSpace() {
        val tail = runs.last()
        if (tail.image == null && tail.text.lastOrNull() == ' ') tail.text.setLength(tail.text.length - 1)
    }

    private fun remember(appended: String) {
        last = appended.last()
        beforeLast = if (appended.length > 1) appended[appended.length - 2] else last
    }

    private companion object {
        const val NEWLINE = '\n'
        const val IMAGE = '\uFFFC' // Object replacement character: not whitespace, not a line start.
        val WHITESPACE = Regex("[ \\t\\r\\n\\u000c\\u00a0]+")
    }
}
