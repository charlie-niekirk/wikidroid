package dev.cniekirk.wikidroid.feature.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import org.junit.Test

class SeedSuggestionTest {
    @Test
    fun twoNumbersAreAPosition() {
        assertThat(parseCoordinates("120 -340")).isEqualTo(BlockPos(120, -340))
        assertThat(parseCoordinates("120, -340")).isEqualTo(BlockPos(120, -340))
        assertThat(parseCoordinates("  120,-340 ")).isEqualTo(BlockPos(120, -340))
        assertThat(parseCoordinates("x120 z-340")).isEqualTo(BlockPos(120, -340))
        assertThat(parseCoordinates("X: 120 Z: -340")).isEqualTo(BlockPos(120, -340))
    }

    @Test
    fun threeNumbersAreAPositionWithItsHeightDropped() {
        assertThat(parseCoordinates("120 64 -340")).isEqualTo(BlockPos(120, -340))
        assertThat(parseCoordinates("x=120 y=64 z=-340")).isEqualTo(BlockPos(120, -340))
    }

    @Test
    fun oneNumberIsASeedNotAPosition() {
        assertThat(parseCoordinates("262")).isNull()
        assertThat(parseCoordinates("-4172144997902289642")).isNull()
    }

    @Test
    fun wordsAndOtherShapesAreNotPositions() {
        assertThat(parseCoordinates("glacier")).isNull()
        assertThat(parseCoordinates("1 2 3 4")).isNull()
        assertThat(parseCoordinates("12 abc")).isNull()
        assertThat(parseCoordinates("")).isNull()
    }

    @Test
    fun aPositionIsPulledInsideTheWorldBorder() {
        val limit = SeedMapEngine.MAX_COORDINATE
        assertThat(parseCoordinates("99999999999 -99999999999")).isEqualTo(BlockPos(limit, -limit))
    }

    private val village = SavedSeed(seed = 262, version = "26.3", label = "Spawn village")
    private val plain = SavedSeed(seed = -99, version = "1.12", label = null)

    @Test
    fun nothingTypedOffersRandomAndEverySavedSeed() {
        assertThat(seedSuggestions("", listOf(village, plain)))
            .containsExactly(
                SeedSuggestion.Random,
                SeedSuggestion.Saved(village),
                SeedSuggestion.Saved(plain),
            ).inOrder()
    }

    @Test
    fun typedTextIsOfferedAsASeedFirst() {
        assertThat(seedSuggestions(" glacier ", emptyList()))
            .containsExactly(SeedSuggestion.UseSeed("glacier"), SeedSuggestion.Random)
            .inOrder()
    }

    @Test
    fun typedCoordinatesAreOfferedAsAPlaceInsteadOfASeed() {
        assertThat(seedSuggestions("10 20", emptyList()))
            .containsExactly(SeedSuggestion.GoTo(BlockPos(10, 20)), SeedSuggestion.Random)
            .inOrder()
    }

    @Test
    fun savedSeedsMatchTheirNameOrNumber() {
        assertThat(seedSuggestions("VILLAGE", listOf(village, plain))).contains(SeedSuggestion.Saved(village))
        assertThat(seedSuggestions("VILLAGE", listOf(village, plain))).doesNotContain(SeedSuggestion.Saved(plain))
        assertThat(seedSuggestions("-99", listOf(village, plain))).contains(SeedSuggestion.Saved(plain))
    }

    @Test
    fun thePillShowsTheSeedAsTypedWhileItStillIsTheSeed() {
        assertThat(shownSeed(262, null)).isEqualTo("262")
        assertThat(shownSeed("glacier".hashCode().toLong(), "glacier")).isEqualTo("glacier")
        assertThat(shownSeed(7, "glacier")).isEqualTo("7")
        assertThat(shownSeed(7, "  ")).isEqualTo("7")
    }
}
