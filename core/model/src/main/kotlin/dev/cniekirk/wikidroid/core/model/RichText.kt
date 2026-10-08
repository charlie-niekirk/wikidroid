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

    val isBlank: Boolean get() = spans.all { it.text.isBlank() }

    companion object {
        val Empty = RichText(persistentListOf())

        fun of(text: String): RichText = if (text.isEmpty()) Empty else RichText(persistentListOf(RichSpan(text)))

        fun of(vararg spans: RichSpan): RichText = RichText(spans.toList().toImmutableList())
    }
}

@Immutable
data class RichSpan(
    val text: String,
    val styles: Set<TextStyleFlag> = emptySet(),
    val link: Link? = null,
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
