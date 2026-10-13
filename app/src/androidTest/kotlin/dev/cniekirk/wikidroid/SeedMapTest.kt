package dev.cniekirk.wikidroid

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.StructureType
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

    /** The default view (block 0, 0 at 2 blocks per pixel) draws the 4-block-per-cell tile touching the origin. */
    private fun originTile(world: MapWorld) = TileKey(world, TileScale.CELL_4, 0, 0)

    private fun waitForTile(key: TileKey): Bitmap {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) { testApp.graph.tileCache.peek(key) != null }
        return testApp.graph.tileCache.peek(key)!!
    }

    private fun openSeedMap() {
        composeRule.openTab("Seed map")
        composeRule.waitUntilAtLeastOneExists(hasTestTag("seed-map-canvas"), UI_TIMEOUT_MILLIS)
    }

    /** The pill and the expanded search share one text field; once expanded, the search's copy is the last. */
    private fun seedField() = composeRule.onAllNodesWithTag("seed-map-seed-field").onLast()

    /** Types [text] into the seed search and presses the search key, which runs the first suggestion. */
    private fun search(text: String) {
        seedField().performClick()
        seedField().performTextClearance()
        seedField().performTextInput(text)
        seedField().performImeAction()
    }

    /** Opens the layers sheet and picks [version] from its menu; the sheet stays open. */
    private fun pickVersion(version: String) {
        composeRule.onNodeWithTag("seed-map-world-chip").performClick()
        composeRule.onNodeWithTag("seed-map-version-field").performClick()
        composeRule.onNodeWithTag("seed-map-version-$version").performScrollTo().performClick()
    }

    @Test
    fun theTabOpensOnTheDefaultSeedAndRendersARealTile() {
        openSeedMap()

        composeRule.tab("Seed map").assertIsDisplayed()
        composeRule.onNodeWithTag("seed-map-world-chip").assertTextContains("26.3", substring = true)
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

        pickVersion("V1_12")

        waitForTile(originTile(MapWorld(262, McVersion.V1_12)))
        composeRule.onNodeWithTag("seed-map-world-chip").assertTextContains("1.12", substring = true)
        composeRule.onNodeWithTag("seed-map-version-menu").assertDoesNotExist()
    }

    @Test
    fun theDimensionChipSwitchesToTheNetherAndRendersItsMap() {
        openSeedMap()
        waitForTile(originTile(MapWorld(262, McVersion.newest)))

        composeRule.onNodeWithTag("seed-map-world-chip").performClick()
        composeRule.onNodeWithTag("seed-map-dimension-NETHER").performClick()

        // Entering the Nether keeps the centre (the origin) and zooms in 8x, so the finest tile is on screen.
        val nether = MapWorld(262, McVersion.newest, Dimension.NETHER)
        val tile = waitForTile(TileKey(nether, TileScale.CELL_4, 0, 0))
        val pixels =
            IntArray(tile.width * tile.height).also {
                tile.getPixels(it, 0, tile.width, 0, 0, tile.width, tile.height)
            }
        assertThat(pixels.toSet()).isNotEmpty()
        composeRule.onNodeWithTag("seed-map-world-chip").assertTextContains("Nether", substring = true)
        composeRule.onNodeWithTag("seed-map-center").assertTextContains("Overworld X 0, Z 0", substring = true)
    }

    @Test
    fun anOlderVersionCannotPickTheNether() {
        openSeedMap()
        pickVersion("V1_12")

        // The layers sheet is still open, so its Nether card is right there.
        composeRule.onNodeWithTag("seed-map-dimension-NETHER").assertIsNotEnabled()
    }

    @Test
    fun theLegendListsBiomesAndFindsThemBySearch() {
        openSeedMap()

        composeRule.onNodeWithTag("seed-map-legend").performClick()
        composeRule.onNodeWithTag("seed-map-legend-search").performTextInput("cherry")

        composeRule.onNodeWithText("Cherry Grove").assertIsDisplayed()
        composeRule.onNodeWithText("Plains").assertDoesNotExist()
    }

    @Test
    fun aVillagePinStillOpensItsSheetAndArticle() {
        openSeedMap()
        waitForTile(originTile(MapWorld(262, McVersion.newest)))
        val village =
            runBlocking {
                testApp.graph.seedMapEngine
                    .structuresIn(
                        MapWorld(262, McVersion.newest),
                        StructureType.VILLAGE,
                        BlockArea(-2048, -2048, 2048, 2048),
                    ).first()
            }

        search("${village.pos.x} ${village.pos.z}")
        // The pin search waits for the view to settle before it answers.
        Thread.sleep(PIN_SETTLE_MILLIS)
        composeRule.onNodeWithTag("seed-map-canvas").performTouchInput { click(center) }

        composeRule.waitUntilAtLeastOneExists(hasTestTag("seed-map-selection"), UI_TIMEOUT_MILLIS)
        composeRule.onNodeWithTag("seed-map-selection-title").assertTextEquals("Village")
        composeRule.onNodeWithTag("seed-map-open-article").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("article-list"), UI_TIMEOUT_MILLIS)
    }

    @Test
    fun aNewSeedRendersItsOwnMap() {
        openSeedMap()
        waitForTile(originTile(MapWorld(262, McVersion.newest)))

        search("2621")

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

        composeRule.onNodeWithTag("seed-map-save-seed").performClick()
        composeRule.onNodeWithTag("seed-map-save-name").performTextInput("Spawn village")
        composeRule.onNodeWithTag("seed-map-save-confirm").performClick()

        val saved =
            runBlocking {
                testApp.graph.seedRepository.savedSeeds
                    .first { it.isNotEmpty() }
            }
        assertThat(saved.map { it.seed to it.label }).containsExactly(262L to "Spawn village")
        composeRule.onNodeWithText("· Spawn village", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove this seed from saved seeds").assertIsDisplayed()
        seedField().performClick()
        composeRule.onNodeWithText("Spawn village").assertIsDisplayed()
    }

    @Test
    fun leavingTheTabAndComingBackKeepsTheMap() {
        openSeedMap()
        waitForTile(originTile(MapWorld(262, McVersion.newest)))

        composeRule.openTab("Explore")
        composeRule.waitForText("Latest versions")
        composeRule.openTab("Seed map")

        composeRule.waitUntilAtLeastOneExists(hasTestTag("seed-map-canvas"), UI_TIMEOUT_MILLIS)
        composeRule.onNodeWithTag("seed-map-world-chip").assertTextContains("26.3", substring = true)
    }
}

private const val PIN_SETTLE_MILLIS = 1_500L
