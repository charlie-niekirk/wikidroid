package dev.cniekirk.wikidroid.feature.seedmap

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.BiomeLegendEntry
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.StructurePos
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.seedmap.TILE_CELLS
import dev.cniekirk.wikidroid.core.seedmap.TileCache
import dev.cniekirk.wikidroid.core.seedmap.TileRenderer
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import dev.cniekirk.wikidroid.core.testing.fake.FakeSeedMapEngine
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test

private const val SNACKBAR_SHORT_MILLIS = 10_000L

/** The floating controls: the seed search, saved seeds, and the layers sheet the World chip opens. */
class SeedMapControlsTest : ComposeTest() {
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

    /** Everything except the canvas reporting its size, which happens once the screen is laid out. */
    private val userActions get() = actions.filterNot { it is SeedMapAction.ViewportChanged }

    // region seed search

    /** The pill and the expanded search share one text field; once expanded, the search's copy is the last. */
    private fun seedField() = composeRule.onAllNodesWithTag(SEED_FIELD_TAG).onLast()

    private fun search(text: String) {
        seedField().performClick()
        seedField().performTextClearance()
        seedField().performTextInput(text)
    }

    @Test
    fun showsTheSeedAndTheWorld() {
        setScreen(SeedMapState(seed = 262, version = McVersion.V1_21_1))

        seedField().assertTextContains("262")
        composeRule.onNodeWithTag(WORLD_CHIP_TAG).assertTextContains("1.21.1 · Overworld")
    }

    @Test
    fun theChipsSitUnderThePill() {
        setScreen()

        val field = composeRule.onNodeWithTag(SEED_FIELD_TAG).fetchSemanticsNode().boundsInRoot
        val world = composeRule.onNodeWithTag(WORLD_CHIP_TAG).fetchSemanticsNode().boundsInRoot
        val structures = composeRule.onNodeWithTag(STRUCTURES_CHIP_TAG).fetchSemanticsNode().boundsInRoot

        assertThat(world.top).isAtLeast(field.bottom)
        assertThat(world.right).isAtMost(structures.left)
    }

    @Test
    fun theMapFillsTheScreenUnderTheFloatingControls() {
        setScreen()

        val canvas = composeRule.onNodeWithTag(SEED_MAP_CANVAS_TAG).fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(canvas).isEqualTo(root)
    }

    @Test
    fun submittingTheSearchSendsTheTypedSeed() {
        setScreen()

        search("Hello World")
        composeRule.onNodeWithTag(USE_SEED_SUGGESTION_TAG).assertTextContains("Hello World", substring = true)
        seedField().performImeAction()

        assertThat(userActions).containsExactly(SeedMapAction.SubmitSeed("Hello World"))
    }

    @Test
    fun typedCoordinatesOfferToGoThere() {
        setScreen()

        search("120, -340")

        composeRule.onNodeWithTag(GO_TO_SUGGESTION_TAG).assertTextEquals("Go to X 120, Z -340")
        composeRule.onNodeWithTag(USE_SEED_SUGGESTION_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(GO_TO_SUGGESTION_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.GoToCoordinates(120, -340))
    }

    @Test
    fun theSearchKeyGoesToTypedCoordinates() {
        setScreen()

        search("x100 z-200")
        seedField().performImeAction()

        assertThat(userActions).containsExactly(SeedMapAction.GoToCoordinates(100, -200))
    }

    @Test
    fun aStructureNameOffersTheNearestOne() {
        setScreen()

        search("strong")

        composeRule
            .onNodeWithTag(
                nearestSuggestionTag(StructureType.STRONGHOLD),
            ).assertTextContains("Nearest Stronghold")
        composeRule.onNodeWithTag(nearestSuggestionTag(StructureType.STRONGHOLD)).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.GoToNearestStructure(StructureType.STRONGHOLD))
    }

    @Test
    fun theSearchKeyFindsTheNearestStructureBeforeUsingTheWordsAsASeed() {
        setScreen()

        search("village")
        seedField().performImeAction()

        assertThat(userActions).containsExactly(SeedMapAction.GoToNearestStructure(StructureType.VILLAGE))
    }

    @Test
    fun aStructureNameStillOffersItselfAsASeed() {
        setScreen()

        search("village")
        composeRule.onNodeWithTag(USE_SEED_SUGGESTION_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.SubmitSeed("village"))
    }

    @Test
    fun theMapSaysWhatItIsFinding() {
        setScreen(SeedMapState(findingNearest = StructureType.MANSION, pinsHidden = true))

        composeRule.onNodeWithTag(FINDING_CHIP_TAG).assertTextContains("Finding the nearest Woodland Mansion")
        composeRule.onNodeWithText("Zoom in to see structures").assertDoesNotExist()
    }

    @Test
    fun nothingFoundShowsASnackbarThenAcknowledgesIt() {
        setScreen(SeedMapState(nearestMissing = StructureType.ANCIENT_CITY))

        composeRule.onNodeWithText("No Ancient City within 18,432 blocks of here").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(SNACKBAR_SHORT_MILLIS)
        composeRule.waitForIdle()

        assertThat(userActions).containsExactly(SeedMapAction.NearestMissingShown)
    }

    @Test
    fun theRandomRowAsksForARandomSeed() {
        setScreen()
        seedField().performClick()

        composeRule.onNodeWithTag(RANDOM_SEED_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.SubmitSeed(""))
    }

    @Test
    fun theFieldShowsASeedThatChangedOutsideIt() {
        var state by mutableStateOf(SeedMapState(seed = 262))
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                SeedMapScreen(state = state, tiles = tiles, onAction = {}, camera = camera)
            }
        }

        state = state.copy(seed = -4172144997902289642)
        composeRule.waitForIdle()

        seedField().assertTextContains("-4172144997902289642")
    }

    @Test
    fun aSeedTypedAsWordsIsNotReplacedByItsHash() {
        var state by mutableStateOf(SeedMapState(seed = 262))
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                SeedMapScreen(state = state, tiles = tiles, onAction = {}, camera = camera)
            }
        }
        search("glacier")
        seedField().performImeAction()

        state = state.copy(seed = "glacier".hashCode().toLong())
        composeRule.waitForIdle()

        seedField().assertTextContains("glacier")
    }

    // endregion

    // region saved seeds

    private val savedA = SavedSeed(seed = 262, version = "26.3", label = "Spawn village")
    private val savedB = SavedSeed(seed = -99, version = "1.12", label = null)

    @Test
    fun theSearchListsSavedSeedsByNameOrSeed() {
        setScreen(SeedMapState(savedSeeds = persistentListOf(savedA, savedB)))

        seedField().performClick()

        composeRule.onNodeWithText("Spawn village").assertIsDisplayed()
        composeRule.onNodeWithText("Seed 262 · Java 26.3").assertIsDisplayed()
        composeRule.onNodeWithText("-99").assertIsDisplayed()
        composeRule.onNodeWithText("Seed -99 · Java 1.12").assertIsDisplayed()
    }

    @Test
    fun typingNarrowsTheSavedSeeds() {
        setScreen(SeedMapState(savedSeeds = persistentListOf(savedA, savedB)))

        search("spawn")

        composeRule.onNodeWithTag(savedSeedTag(savedA)).assertIsDisplayed()
        composeRule.onNodeWithTag(savedSeedTag(savedB)).assertDoesNotExist()
    }

    @Test
    fun noSavedSeedsSaysSo() {
        setScreen()

        seedField().performClick()

        composeRule.onNodeWithText("No saved seeds yet").assertIsDisplayed()
    }

    @Test
    fun choosingASavedSeedLoadsIt() {
        setScreen(SeedMapState(savedSeeds = persistentListOf(savedA, savedB)))
        seedField().performClick()

        composeRule.onNodeWithTag(savedSeedTag(savedA)).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.LoadSavedSeed(savedA))
    }

    @Test
    fun theTrashButtonRemovesASavedSeed() {
        setScreen(SeedMapState(savedSeeds = persistentListOf(savedA, savedB)))
        seedField().performClick()

        composeRule.onNodeWithContentDescription("Remove Spawn village from saved seeds").performClick()

        assertThat(userActions).containsExactly(SeedMapAction.DeleteSavedSeed(savedA))
    }

    @Test
    fun theBookmarkSavesTheSeedWithAnOptionalName() {
        setScreen()

        composeRule.onNodeWithTag(SAVE_SEED_TAG).assertContentDescriptionEquals("Save this seed")
        composeRule.onNodeWithTag(SAVE_SEED_TAG).performClick()
        composeRule.onNodeWithTag(SAVE_NAME_TAG).performTextInput("Mushroom island")
        composeRule.onNodeWithTag(SAVE_CONFIRM_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.SaveSeed("Mushroom island"))
    }

    @Test
    fun savingWithoutANameSendsWhatWasTyped() {
        setScreen()
        composeRule.onNodeWithTag(SAVE_SEED_TAG).performClick()

        composeRule.onNodeWithTag(SAVE_CONFIRM_TAG).performClick()

        // The ViewModel turns an empty name into no label.
        assertThat(userActions).containsExactly(SeedMapAction.SaveSeed(""))
    }

    @Test
    fun aSavedSeedShowsItsNameAndTheBookmarkRemovesIt() {
        setScreen(SeedMapState(seed = 262, version = McVersion.V26_3, savedSeeds = persistentListOf(savedA)))

        composeRule.onNodeWithText("· Spawn village", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(SAVE_SEED_TAG).assertContentDescriptionEquals("Remove this seed from saved seeds")
        composeRule.onNodeWithTag(SAVE_SEED_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.DeleteSavedSeed(savedA))
    }

    // endregion

    // region layers sheet

    private fun openLayers() {
        composeRule.onNodeWithTag(WORLD_CHIP_TAG).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun theWorldChipOpensTheLayersSheetWithAllThreeDimensions() {
        setScreen()
        composeRule.onNodeWithTag(LAYERS_SHEET_TAG).assertDoesNotExist()

        openLayers()

        composeRule.onNodeWithTag(LAYERS_SHEET_TAG).assertIsDisplayed()
        Dimension.entries.forEach { composeRule.onNodeWithTag(dimensionItemTag(it)).assertIsDisplayed() }
        composeRule.onNodeWithTag(dimensionItemTag(Dimension.OVERWORLD)).assertIsSelected()
        composeRule.onNodeWithTag(dimensionItemTag(Dimension.NETHER)).assertIsNotSelected()
    }

    @Test
    fun choosingADimensionSendsItAndKeepsTheSheetOpen() {
        setScreen(SeedMapState(version = McVersion.newest))
        openLayers()

        composeRule.onNodeWithTag(dimensionItemTag(Dimension.NETHER)).performClick()
        composeRule.waitForIdle()

        assertThat(userActions).containsExactly(SeedMapAction.SelectDimension(Dimension.NETHER))
        composeRule.onNodeWithTag(LAYERS_SHEET_TAG).assertIsDisplayed()
    }

    @Test
    fun dimensionsTheVersionLacksAreDisabledAndSayWhy() {
        setScreen(SeedMapState(version = McVersion.V1_12))
        openLayers()

        composeRule.onNodeWithTag(dimensionItemTag(Dimension.OVERWORLD)).assertIsEnabled()
        composeRule.onNodeWithTag(dimensionItemTag(Dimension.END)).assertIsEnabled()
        composeRule.onNodeWithTag(dimensionItemTag(Dimension.NETHER)).assertIsNotEnabled()
        composeRule.onNodeWithText("Java Edition 1.16.1 and newer").assertIsDisplayed()

        composeRule.onNodeWithTag(dimensionItemTag(Dimension.NETHER)).performClick()

        assertThat(userActions).isEmpty()
    }

    @Test
    fun beforeBeta19OnlyTheOverworldCanBePicked() {
        setScreen(SeedMapState(version = McVersion.B1_7))
        openLayers()

        composeRule.onNodeWithTag(dimensionItemTag(Dimension.NETHER)).assertIsNotEnabled()
        composeRule.onNodeWithTag(dimensionItemTag(Dimension.END)).assertIsNotEnabled()
        composeRule.onNodeWithTag(dimensionItemTag(Dimension.OVERWORLD)).assertIsEnabled()
    }

    @Test
    fun theWorldChipWearsTheIconOfItsDimension() {
        var state by mutableStateOf(SeedMapState(dimension = Dimension.OVERWORLD))
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                SeedMapScreen(state = state, tiles = tiles, onAction = {}, camera = camera)
            }
        }
        composeRule.onNodeWithTag(dimensionIconTag(Dimension.OVERWORLD), useUnmergedTree = true).assertExists()

        state = state.copy(dimension = Dimension.NETHER)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(dimensionIconTag(Dimension.NETHER), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(dimensionIconTag(Dimension.OVERWORLD), useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(WORLD_CHIP_TAG).assertTextContains("Nether", substring = true)

        state = state.copy(dimension = Dimension.END)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(dimensionIconTag(Dimension.END), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(WORLD_CHIP_TAG).assertTextContains("End", substring = true)
    }

    @Test
    fun theDimensionCardsShowTheSameIcons() {
        setScreen(SeedMapState(version = McVersion.newest))
        openLayers()

        Dimension.entries.forEach {
            composeRule.onAllNodesWithTag(dimensionIconTag(it), useUnmergedTree = true).onLast().assertIsDisplayed()
        }
    }

    @Test
    fun theVersionMenuListsEveryVersionNewestFirst() {
        setScreen(SeedMapState(version = McVersion.V1_21_1))
        openLayers()
        composeRule.onNodeWithTag(VERSION_FIELD_TAG).assertTextContains("Java Edition 1.21.1")

        composeRule.onNodeWithTag(VERSION_FIELD_TAG).performClick()

        val newest = composeRule.onNodeWithTag(versionItemTag(McVersion.newest)).fetchSemanticsNode().boundsInRoot
        val older = composeRule.onNodeWithTag(versionItemTag(McVersion.V1_21_11)).fetchSemanticsNode().boundsInRoot
        assertThat(newest.top).isLessThan(older.top)
    }

    @Test
    fun choosingAVersionSendsItAndClosesTheMenu() {
        setScreen(SeedMapState(version = McVersion.newest))
        openLayers()
        composeRule.onNodeWithTag(VERSION_FIELD_TAG).performClick()

        composeRule.onNodeWithTag(versionItemTag(McVersion.V26_1)).performClick()
        composeRule.waitForIdle()

        assertThat(userActions).containsExactly(SeedMapAction.SelectVersion(McVersion.V26_1))
        composeRule.onNodeWithTag(VERSION_MENU_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(LAYERS_SHEET_TAG).assertIsDisplayed()
    }

    @Test
    fun theVersionMenuReachesTheOldestVersionToo() {
        setScreen()
        openLayers()
        composeRule.onNodeWithTag(VERSION_FIELD_TAG).performClick()

        composeRule.onNodeWithTag(versionItemTag(McVersion.B1_7)).performScrollTo().performClick()

        assertThat(userActions).containsExactly(SeedMapAction.SelectVersion(McVersion.B1_7))
    }

    @Test
    fun theDisplaySwitchesSendTheirActions() {
        setScreen(SeedMapState(showGrid = false, showCrosshair = true))
        openLayers()

        composeRule
            .onNodeWithTag(GRID_SWITCH_TAG)
            .performScrollTo()
            .assertIsOff()
            .performClick()
        composeRule
            .onNodeWithTag(CROSSHAIR_SWITCH_TAG)
            .performScrollTo()
            .assertIsOn()
            .performClick()

        assertThat(userActions)
            .containsExactly(SeedMapAction.SetGrid(true), SeedMapAction.SetCrosshair(false))
            .inOrder()
    }

    // endregion
}
