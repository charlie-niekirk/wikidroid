package dev.cniekirk.wikidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RichTextTest {
    @Test
    fun plainText_joinsSpans() {
        val text =
            RichText.of(
                RichSpan("A "),
                RichSpan("diamond", setOf(TextStyleFlag.Bold), Link.Internal("Diamond")),
                RichSpan(" pickaxe"),
            )

        assertThat(text.plainText).isEqualTo("A diamond pickaxe")
    }

    @Test
    fun of_emptyString_isEmpty() {
        assertThat(RichText.of("")).isEqualTo(RichText.Empty)
        assertThat(RichText.Empty.isBlank).isTrue()
    }

    @Test
    fun isBlank_falseWhenAnySpanHasText() {
        assertThat(RichText.of(RichSpan(" "), RichSpan("x")).isBlank).isFalse()
    }
}
