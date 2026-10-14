package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.TILE_CELLS
import dev.cniekirk.wikidroid.core.seedmap.TileCache
import dev.cniekirk.wikidroid.core.seedmap.TileRenderer
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import dev.cniekirk.wikidroid.core.testing.fake.FakeSeedMapEngine
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Test

/** The ruler (toolbar button, bar, taps, "Measure from here") and the My worlds sheet. */
class MeasureAndWorldsTest : ComposeTest() {
    private val actions = mutableListOf<SeedMapAction>()
    private val engine = FakeSeedMapEngine()
    private val tiles = TileCache(engine, TileRenderer(engine), maxBytes = 64 * TILE_CELLS * TILE_CELLS * 4)
    private val camera = MapCamera(blocksPerPixel = 1f)

    private fun setScreen(state: SeedMapState = SeedMapState()) {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                SeedMapScreen(state = state, tiles = tiles, onAction = { actions += it }, camera = camera)
            }
        }
    }

    private val userActions get() = actions.filterNot { it is SeedMapAction.ViewportChanged }

    // region measure

    @Test
    fun theRulerButtonStartsMeasuring() {
        setScreen()

        composeRule.onNodeWithTag(MEASURE_BUTTON_TAG).assertContentDescriptionEquals("Measure distance").performClick()

        assertThat(userActions).containsExactly(SeedMapAction.StartMeasure)
    }

    @Test
    fun theRulerButtonAndTheBarsCloseStopMeasuring() {
        setScreen(SeedMapState(measure = Measurement()))

        composeRule.onNodeWithTag(MEASURE_BUTTON_TAG).assertContentDescriptionEquals("Stop measuring").performClick()
        composeRule.onNodeWithTag(MEASURE_CLOSE_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.StopMeasure, SeedMapAction.StopMeasure)
    }

    @Test
    fun theBarSaysWhatToTapThenTheDistanceInBothDimensions() {
        var state by mutableStateOf(SeedMapState(measure = Measurement()))
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                SeedMapScreen(state = state, tiles = tiles, onAction = {}, camera = camera)
            }
        }
        val text = composeRule.onNodeWithTag(MEASURE_TEXT_TAG)
        text.assertTextEquals("Tap where to measure from")

        state = state.copy(measure = Measurement(start = BlockPos(0, 0)))
        text.assertTextEquals("Tap where to measure to")

        state = state.copy(measure = Measurement(BlockPos(0, 0), BlockPos(600, 800)))
        text.assertTextEquals("1,000 blocks · 125 through the Nether")

        state = state.copy(dimension = Dimension.NETHER)
        text.assertTextEquals("1,000 blocks · 8,000 in the Overworld")

        state = state.copy(dimension = Dimension.END)
        text.assertTextEquals("1,000 blocks")
    }

    @Test
    fun whileMeasuringATapIsAPointNotASelection() {
        setScreen(SeedMapState(measure = Measurement()))

        composeRule.onNodeWithTag(SEED_MAP_CANVAS_TAG).performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WINDOW_MILLIS)
        composeRule.waitForIdle()

        assertThat(userActions).containsExactly(SeedMapAction.MeasurePoint(BlockPos(0, 0)))
    }

    @Test
    fun aSelectionCanBeMeasuredFrom() {
        setScreen(SeedMapState(selection = MapSelection.Spawn(BlockPos(40, -8))))

        composeRule.onNodeWithTag(MEASURE_FROM_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.MeasureFrom(BlockPos(40, -8)))
    }

    // endregion

    // region my worlds

    private val base = SavedSeed(262, "26.3", "Base")
    private val island = SavedSeed(-99, "1.12", null)

    private fun openWorlds() {
        composeRule.onAllNodesWithTag(SEED_FIELD_TAG).onLast().performClick()
        composeRule.onNodeWithTag(SUGGESTIONS_TAG).performScrollToNode(hasTestTag(WORLDS_BUTTON_TAG))
        composeRule.onNodeWithTag(WORLDS_BUTTON_TAG).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun myWorldsListsSavedSeedsWithTheirPoints() {
        setScreen(
            SeedMapState(savedSeeds = persistentListOf(base, island), poiCounts = persistentMapOf(262L to 2)),
        )

        openWorlds()

        composeRule.onNodeWithTag(worldRowTag(base)).assertTextContains("Seed 262 · Java 26.3 · 2 points")
        composeRule.onNodeWithTag(worldRowTag(island)).assertTextContains("Seed -99 · Java 1.12 · 0 points")
        // The pill's seed (262) in its version is saved as "Base", so that row is the one on the map.
        composeRule.onNodeWithTag(worldRowTag(base)).assert(hasContentDescription("On the map now"))
        composeRule.onNodeWithTag(worldRowTag(island)).assert(!hasContentDescription("On the map now"))
    }

    @Test
    fun tappingAWorldLoadsItAndClosesTheSheet() {
        setScreen(SeedMapState(savedSeeds = persistentListOf(base, island)))
        openWorlds()

        composeRule.onNodeWithTag(worldRowTag(island)).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.LoadSavedSeed(island))
        composeRule.onNodeWithTag(WORLDS_LIST_TAG).assertDoesNotExist()
    }

    @Test
    fun renamingStartsFromTheCurrentName() {
        setScreen(SeedMapState(savedSeeds = persistentListOf(base)))
        openWorlds()

        composeRule.onNodeWithTag(worldRenameTag(base)).performClick()
        composeRule.onNodeWithTag(SAVE_NAME_TAG).assertTextContains("Base")
        composeRule.onNodeWithTag(SAVE_NAME_TAG).performTextClearance()
        composeRule.onNodeWithTag(SAVE_NAME_TAG).performTextInput("Home")
        composeRule.onNodeWithTag(SAVE_CONFIRM_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.RenameSavedSeed(base, "Home"))
    }

    @Test
    fun removingAsksFirstAndSaysThePointsStay() {
        setScreen(SeedMapState(savedSeeds = persistentListOf(base)))
        openWorlds()

        composeRule.onNodeWithTag(worldRemoveTag(base)).performClick()
        composeRule.onNodeWithText("Remove Base?").assertIsDisplayed()
        composeRule.onNodeWithText("points of interest stay", substring = true).assertIsDisplayed()
        assertThat(userActions).isEmpty()
        composeRule.onNodeWithTag(REMOVE_CONFIRM_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.DeleteSavedSeed(base))
    }

    @Test
    fun withNothingSavedMyWorldsSaysHowToSave() {
        setScreen()

        openWorlds()

        composeRule.onNodeWithTag(WORLDS_EMPTY_TAG).assertIsDisplayed()
    }

    // endregion

    private companion object {
        const val DOUBLE_TAP_WINDOW_MILLIS = 500L
    }
}
