package dev.cniekirk.wikidroid.feature.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.PointOfInterest
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.cniekirk.wikidroid.core.seedmap.StructureType
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

    private val world = MapWorld(262, McVersion.newest)
    private val nether = world.copy(dimension = Dimension.NETHER)

    private val village = SavedSeed(seed = 262, version = "26.3", label = "Spawn village")
    private val plain = SavedSeed(seed = -99, version = "1.12", label = null)

    @Test
    fun nothingTypedOffersRandomAndEverySavedSeed() {
        assertThat(seedSuggestions("", listOf(village, plain), world))
            .containsExactly(
                SeedSuggestion.Random,
                SeedSuggestion.Saved(village),
                SeedSuggestion.Saved(plain),
            ).inOrder()
    }

    @Test
    fun typedTextIsOfferedAsASeedFirst() {
        assertThat(seedSuggestions(" glacier ", emptyList(), world))
            .containsExactly(SeedSuggestion.UseSeed("glacier"), SeedSuggestion.Random)
            .inOrder()
    }

    @Test
    fun typedCoordinatesAreOfferedAsAPlaceInsteadOfASeed() {
        assertThat(seedSuggestions("10 20", emptyList(), world))
            .containsExactly(SeedSuggestion.GoTo(BlockPos(10, 20)), SeedSuggestion.Random)
            .inOrder()
    }

    @Test
    fun aStructureNameOffersTheNearestOneBeforeTheSeed() {
        assertThat(seedSuggestions("strong", emptyList(), world))
            .containsExactly(
                SeedSuggestion.Nearest(StructureType.STRONGHOLD),
                SeedSuggestion.UseSeed("strong"),
                SeedSuggestion.Random,
            ).inOrder()
    }

    @Test
    fun structuresMatchAnywhereInTheirNameIgnoringCase() {
        assertThat(structuresMatching("PYRAMID", world))
            .containsExactly(StructureType.DESERT_PYRAMID, StructureType.JUNGLE_TEMPLE)
    }

    @Test
    fun onlyStructuresOfTheMapsWorldMatch() {
        assertThat(structuresMatching("ruined portal", world)).containsExactly(StructureType.RUINED_PORTAL)
        assertThat(structuresMatching("ruined portal", nether)).containsExactly(StructureType.RUINED_PORTAL_NETHER)
        assertThat(structuresMatching("village", nether)).isEmpty()
        assertThat(structuresMatching("trial", world.copy(version = McVersion.V1_12))).isEmpty()
    }

    @Test
    fun oneLetterMatchesNoStructure() {
        assertThat(structuresMatching("v", world)).isEmpty()
    }

    @Test
    fun theSearchKeyRunsRowsForTheTypedText() {
        assertThat(SeedSuggestion.GoTo(BlockPos(0, 0)).isForTypedText).isTrue()
        assertThat(SeedSuggestion.Nearest(StructureType.VILLAGE).isForTypedText).isTrue()
        assertThat(SeedSuggestion.UseSeed("x").isForTypedText).isTrue()
        assertThat(SeedSuggestion.Random.isForTypedText).isFalse()
        assertThat(SeedSuggestion.Saved(village).isForTypedText).isFalse()
    }

    @Test
    fun savedSeedsMatchTheirNameOrNumber() {
        assertThat(seedSuggestions("VILLAGE", listOf(village, plain), world)).contains(SeedSuggestion.Saved(village))
        assertThat(
            seedSuggestions("VILLAGE", listOf(village, plain), world),
        ).doesNotContain(SeedSuggestion.Saved(plain))
        assertThat(seedSuggestions("-99", listOf(village, plain), world)).contains(SeedSuggestion.Saved(plain))
    }

    @Test
    fun thePillShowsTheSeedAsTypedWhileItStillIsTheSeed() {
        assertThat(shownSeed(262, null)).isEqualTo("262")
        assertThat(shownSeed("glacier".hashCode().toLong(), "glacier")).isEqualTo("glacier")
        assertThat(shownSeed(7, "glacier")).isEqualTo("7")
        assertThat(shownSeed(7, "  ")).isEqualTo("7")
    }

    private val home = PointOfInterest(id = 1, seed = 262, dimension = "OVERWORLD", x = 5, z = 6, name = "Village home")
    private val farm = PointOfInterest(id = 2, seed = 262, dimension = "OVERWORLD", x = 7, z = 8, name = "Iron farm")
    private val unnamed = PointOfInterest(id = 3, seed = 262, dimension = "OVERWORLD", x = 9, z = 0)

    @Test
    fun aPointsNameOffersGoingToItAfterStructuresAndBeforeTheSeed() {
        assertThat(seedSuggestions("village", emptyList(), world, listOf(home, farm, unnamed)))
            .containsExactly(
                SeedSuggestion.Nearest(StructureType.VILLAGE),
                SeedSuggestion.Poi(home),
                SeedSuggestion.UseSeed("village"),
                SeedSuggestion.Random,
            ).inOrder()
    }

    @Test
    fun pointsMatchAnywhereInTheirNameButNotOnOneLetter() {
        assertThat(poisMatching("FARM", listOf(home, farm, unnamed))).containsExactly(farm)
        assertThat(poisMatching("o", listOf(home, farm, unnamed))).isEmpty()
    }

    @Test
    fun aPointRunsFromTheSearchKey() {
        assertThat(SeedSuggestion.Poi(home).isForTypedText).isTrue()
    }
}
