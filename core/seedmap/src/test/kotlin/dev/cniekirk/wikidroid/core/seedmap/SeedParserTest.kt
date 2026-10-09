package dev.cniekirk.wikidroid.core.seedmap

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class SeedParserTest {
    @Test
    fun `a number is the seed`() {
        assertThat(SeedParser.parse("262")).isEqualTo(262L)
        assertThat(SeedParser.parse("-4172144997902289642")).isEqualTo(-4172144997902289642L)
        assertThat(SeedParser.parse("0")).isEqualTo(0L)
    }

    @Test
    fun `the extremes of a 64-bit seed are kept`() {
        assertThat(SeedParser.parse(Long.MAX_VALUE.toString())).isEqualTo(Long.MAX_VALUE)
        assertThat(SeedParser.parse(Long.MIN_VALUE.toString())).isEqualTo(Long.MIN_VALUE)
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertThat(SeedParser.parse("  262\n")).isEqualTo(262L)
        assertThat(SeedParser.parse(" glacier ")).isEqualTo(SeedParser.parse("glacier"))
    }

    @Test
    fun `a plus sign is accepted like the game accepts it`() {
        assertThat(SeedParser.parse("+17")).isEqualTo(17L)
    }

    @Test
    fun `text is hashed the way Java hashes a string`() {
        // Expected values computed with Java's algorithm, h = 31 * h + char over the UTF-16 units.
        assertThat(SeedParser.parse("A")).isEqualTo(65L)
        assertThat(SeedParser.parse("glacier")).isEqualTo(108_181_935L)
        assertThat(SeedParser.parse("Minecraft")).isEqualTo(-1_595_926_131L)
    }

    @Test
    fun `a hash can be negative and stays a sign-extended int`() {
        val seed = SeedParser.parse("The quick brown fox")
        assertThat(seed).isEqualTo("The quick brown fox".hashCode().toLong())
        assertThat(seed).isAtLeast(Int.MIN_VALUE.toLong())
        assertThat(seed).isAtMost(Int.MAX_VALUE.toLong())
    }

    @Test
    fun `a number too big for 64 bits is treated as text`() {
        val tooBig = "9223372036854775808"
        assertThat(SeedParser.parse(tooBig)).isEqualTo(tooBig.hashCode().toLong())
    }

    @Test
    fun `numbers with a decimal point or separators are text`() {
        assertThat(SeedParser.parse("1.5")).isEqualTo("1.5".hashCode().toLong())
        assertThat(SeedParser.parse("1,000")).isEqualTo("1,000".hashCode().toLong())
    }

    @Test
    fun `blank text picks a random seed`() {
        val random = Random(42)
        val first = SeedParser.parse("", random)
        val second = SeedParser.parse("   ", random)

        assertThat(first).isEqualTo(Random(42).nextLong())
        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `the random source is not used for text`() {
        assertThat(SeedParser.parse("262", Random(1))).isEqualTo(262L)
        assertThat(SeedParser.parse("tundra", Random(1))).isEqualTo("tundra".hashCode().toLong())
    }
}
