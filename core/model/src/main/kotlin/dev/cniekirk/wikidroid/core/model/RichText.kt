package dev.cniekirk.wikidroid.core.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Inline styled text. The UI turns this into an `AnnotatedString`. */
@Immutable
data class RichText(
    val spans: ImmutableList<RichSpan>,
) {
    val plainText: String get() = spans.joinToString(separator = "") { it.text }

    /** `true` when there is neither visible text nor an inline image. */
    val isBlank: Boolean get() = spans.all { it.image == null && it.text.isBlank() }

    /** `true` when there is readable text; icons alone don't count. */
    val hasText: Boolean get() = spans.any { it.image == null && it.text.isNotBlank() }

    companion object {
        val Empty = RichText(persistentListOf())

        fun of(text: String): RichText = if (text.isEmpty()) Empty else RichText(persistentListOf(RichSpan(text)))

        fun of(vararg spans: RichSpan): RichText = RichText(spans.toList().toImmutableList())
    }
}

/**
 * One run of text, or, when [image] is set, one picture that sits in the line like a character.
 * An image span's [text] is its alt text (often empty), so [RichText.plainText] stays readable.
 */
@Immutable
data class RichSpan(
    val text: String,
    val styles: Set<TextStyleFlag> = emptySet(),
    val link: Link? = null,
    val image: InlineImage? = null,
)

/** A small picture inside running text, such as an item sprite beside its name. */
@Immutable
data class InlineImage(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    /** Sprites and pixel art, which must be scaled without smoothing. */
    val pixelated: Boolean = false,
)

enum class TextStyleFlag {
    Bold,
    Italic,
    Underline,
    Strikethrough,
    Code,
    Superscript,
    Subscript,
}

@Immutable
sealed interface Link {
    /** A link to another wiki article, optionally to a section [anchor]. */
    data class Internal(
        val title: String,
        val anchor: String? = null,
    ) : Link

    data class External(
        val url: String,
    ) : Link
}
