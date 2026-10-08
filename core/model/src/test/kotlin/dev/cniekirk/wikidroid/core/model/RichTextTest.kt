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

    @Test
    fun anImageAloneIsNotBlankAndHasNoText() {
        val icon = RichText.of(RichSpan("", image = InlineImage("https://minecraft.wiki/images/D.png", 16, 16)))

        assertThat(icon.isBlank).isFalse()
        assertThat(icon.hasText).isFalse()
        assertThat(icon.plainText).isEmpty()
    }

    @Test
    fun hasText_trueWhenAnySpanHasReadableText() {
        assertThat(RichText.of(RichSpan("x")).hasText).isTrue()
        assertThat(RichText.of(RichSpan(" ")).hasText).isFalse()
        assertThat(RichText.Empty.hasText).isFalse()
    }
}
