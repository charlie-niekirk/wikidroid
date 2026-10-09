package dev.cniekirk.wikidroid

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.TileKey
import dev.cniekirk.wikidroid.core.seedmap.TileScale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The Seed map tab on the real native engine (`libseedmap.so` for the emulator's ABI): a tile is generated
 * and painted, taps open the selection sheet, and "Open wiki article" reaches the article screen served by
 * the mock server (which answers every `parse` request with the same trimmed fixture).
 */
@Suppress("DEPRECATION")
class SeedMapTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ResetAppStateRule()).around(composeRule)

    /** The default view (block 0, 0 at 3 blocks per pixel) draws the 4-block-per-cell tile touching the origin. */
    private fun originTile(world: MapWorld) = TileKey(world, TileScale.CELL_4, 0, 0)

    private fun waitForTile(key: TileKey): Bitmap {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) { testApp.graph.tileCache.peek(key) != null }
        return testApp.graph.tileCache.peek(key)!!
    }

    private fun openSeedMap() {
        composeRule.openTab("Seed map")
        composeRule.waitUntilAtLeastOneExists(hasTestTag("seed-map-canvas"), UI_TIMEOUT_MILLIS)
    }

    @Test
    fun theTabOpensOnTheDefaultSeedAndRendersARealTile() {
        openSeedMap()

        composeRule.tab("Seed map").assertIsDisplayed()
        composeRule.onNodeWithText("Java Edition 26.3").assertIsDisplayed()
        val tile = waitForTile(originTile(MapWorld(262, McVersion.newest)))

        val pixels =
            IntArray(tile.width * tile.height).also {
                tile.getPixels(it, 0, tile.width, 0, 0, tile.width, tile.height)
            }
        // A coloured biome map, not a blank or single-colour bitmap.
        assertThat(pixels.toSet().size).isGreaterThan(3)
        assertThat(pixels.all { it ushr 24 == 0xFF }).isTrue()
    }

    @Test
    fun anotherVersionAsksTheEngineForThatWorld() {
        openSeedMap()
        waitForTile(originTile(MapWorld(262, McVersion.newest)))

        composeRule.onNodeWithTag("seed-map-version").performClick()
        composeRule.onNodeWithText("1.12").performScrollTo().performClick()

        waitForTile(originTile(MapWorld(262, McVersion.V1_12)))
        composeRule.onNodeWithText("Java Edition 1.12").assertIsDisplayed()
    }

    @Test
    fun aNewSeedRendersItsOwnMap() {
        openSeedMap()
        waitForTile(originTile(MapWorld(262, McVersion.newest)))

        composeRule.onNodeWithTag("seed-map-seed-field").performTextInput("1")
        composeRule.onNodeWithTag("seed-map-seed-field").performImeAction()

        // The field held "262", so the typed 1 makes it "2621".
        waitForTile(originTile(MapWorld(2621, McVersion.newest)))
    }

    @Test
    fun draggingTheMapMovesTheCoordinates() {
        openSeedMap()
        waitForTile(originTile(MapWorld(262, McVersion.newest)))

        composeRule.onNodeWithTag("seed-map-canvas").performTouchInput { swipeLeft() }

        composeRule.onNode(hasTestTag("seed-map-center") and !hasText("X 0, Z 0")).assertIsDisplayed()
    }

    @Test
    fun tappingTheMapNamesTheBiomeAndOpensItsArticle() {
        openSeedMap()
        waitForTile(originTile(MapWorld(262, McVersion.newest)))

        composeRule.onNodeWithTag("seed-map-canvas").performTouchInput { click(center) }

        composeRule.waitUntilAtLeastOneExists(hasTestTag("seed-map-selection"), UI_TIMEOUT_MILLIS)
        composeRule.onNodeWithTag("seed-map-open-article").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("article-list"), UI_TIMEOUT_MILLIS)
    }

    @Test
    fun theSpawnButtonSelectsNothingButMovesTheMapToTheSpawn() {
        openSeedMap()
        waitForTile(originTile(MapWorld(262, McVersion.newest)))

        composeRule.onNodeWithTag("seed-map-go-to-spawn").performClick()

        // Seed 262's spawn is not at the origin, so the readout leaves X 0, Z 0.
        composeRule.waitUntilAtLeastOneExists(hasTestTag("seed-map-center") and !hasText("X 0, Z 0"), UI_TIMEOUT_MILLIS)
    }

    @Test
    fun aSavedSeedIsStoredAndListed() {
        openSeedMap()

        composeRule.onNodeWithTag("seed-map-saved-seeds").performClick()
        composeRule.onNodeWithText("Save this seed").performClick()
        composeRule.onNodeWithTag("seed-map-save-name").performTextInput("Spawn village")
        composeRule.onNodeWithTag("seed-map-save-confirm").performClick()

        val saved =
            runBlocking {
                testApp.graph.seedRepository.savedSeeds
                    .first { it.isNotEmpty() }
            }
        assertThat(saved.map { it.seed to it.label }).containsExactly(262L to "Spawn village")
        composeRule.onNodeWithTag("seed-map-saved-seeds").performClick()
        composeRule.onNodeWithText("Spawn village").assertIsDisplayed()
        composeRule.onNodeWithText("This seed is saved").assertIsDisplayed()
    }

    @Test
    fun leavingTheTabAndComingBackKeepsTheMap() {
        openSeedMap()
        waitForTile(originTile(MapWorld(262, McVersion.newest)))

        composeRule.openTab("Explore")
        composeRule.waitForText("Latest versions")
        composeRule.openTab("Seed map")

        composeRule.waitUntilAtLeastOneExists(hasTestTag("seed-map-canvas"), UI_TIMEOUT_MILLIS)
        composeRule.onNodeWithText("Java Edition 26.3").assertIsDisplayed()
    }
}
