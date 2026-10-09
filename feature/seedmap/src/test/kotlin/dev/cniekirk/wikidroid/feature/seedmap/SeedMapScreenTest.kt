package dev.cniekirk.wikidroid.feature.seedmap

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
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
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.SavedSeed
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

private const val DOUBLE_TAP_WINDOW_MILLIS = 500L

class SeedMapScreenTest : ComposeTest() {
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

    private val village = StructurePos(StructureType.VILLAGE, BlockPos(0, 0))

    /** A single tap is only reported once the double-tap window (for the zoom gesture) has passed. */
    private fun tapCentre() {
        composeRule.onNodeWithTag(SEED_MAP_CANVAS_TAG).performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WINDOW_MILLIS)
        composeRule.waitForIdle()
    }

    // region controls

    @Test
    fun showsTheSeedAndVersion() {
        setScreen(SeedMapState(seed = 262, version = McVersion.V1_21_1))

        composeRule.onNodeWithTag(SEED_FIELD_TAG).assertTextContains("262")
        composeRule.onNodeWithTag(VERSION_PICKER_TAG).assertTextContains("Java Edition 1.21.1")
    }

    @Test
    fun submittingTheSeedFieldSendsItsText() {
        setScreen()

        composeRule.onNodeWithTag(SEED_FIELD_TAG).performTextClearance()
        composeRule.onNodeWithTag(SEED_FIELD_TAG).performTextInput("Hello World")
        composeRule.onNodeWithTag(SEED_FIELD_TAG).performImeAction()

        assertThat(userActions).containsExactly(SeedMapAction.SubmitSeed("Hello World"))
    }

    @Test
    fun theDiceButtonAsksForARandomSeed() {
        setScreen()

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

        composeRule.onNodeWithTag(SEED_FIELD_TAG).assertTextContains("-4172144997902289642")
    }

    @Test
    fun aSeedTypedAsWordsIsNotReplacedByItsHash() {
        var state by mutableStateOf(SeedMapState(seed = 262))
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                SeedMapScreen(state = state, tiles = tiles, onAction = {}, camera = camera)
            }
        }
        composeRule.onNodeWithTag(SEED_FIELD_TAG).performTextClearance()
        composeRule.onNodeWithTag(SEED_FIELD_TAG).performTextInput("glacier")

        state = state.copy(seed = "glacier".hashCode().toLong())
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SEED_FIELD_TAG).assertTextContains("glacier")
    }

    @Test
    fun theVersionPickerOffersEveryVersionAndSendsTheChoice() {
        setScreen(SeedMapState(version = McVersion.newest))

        composeRule.onNodeWithTag(VERSION_PICKER_TAG).performClick()
        composeRule.onNodeWithText("1.12").performScrollTo().performClick()

        assertThat(userActions).containsExactly(SeedMapAction.SelectVersion(McVersion.V1_12))
    }

    @Test
    fun theVersionPickerListsTheOldestVersionToo() {
        setScreen()

        composeRule.onNodeWithTag(VERSION_PICKER_TAG).performClick()

        composeRule.onNodeWithText("Beta 1.7").assertExists()
    }

    // endregion

    // region canvas

    @Test
    fun reportsItsViewportOnceMeasured() {
        setScreen()
        composeRule.waitForIdle()

        val reported = actions.filterIsInstance<SeedMapAction.ViewportChanged>().last().viewport
        assertThat(reported.widthPx).isGreaterThan(0)
        assertThat(reported.heightPx).isGreaterThan(0)
        assertThat(reported.blocksPerPixel).isEqualTo(1f)
    }

    @Test
    fun asksTheEngineForTheTilesOnScreen() {
        setScreen()

        composeRule.waitUntil(timeoutMillis = 5_000) { engine.tileRequests.isNotEmpty() }

        assertThat(engine.tileRequests.map { it.world }.toSet()).containsExactly(SeedMapState().world)
    }

    @Test
    fun draggingPansTheMap() {
        setScreen()
        val before = camera.centerX

        composeRule.onNodeWithTag(SEED_MAP_CANVAS_TAG).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        // The map followed the finger left, so the camera moved right.
        assertThat(camera.centerX).isGreaterThan(before)
    }

    @Test
    fun doubleTapZoomsIn() {
        setScreen()

        composeRule.onNodeWithTag(SEED_MAP_CANVAS_TAG).performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()

        assertThat(camera.blocksPerPixel).isEqualTo(0.5f)
    }

    @Test
    fun theZoomButtonsZoom() {
        setScreen()

        composeRule.onNodeWithContentDescription("Zoom in").performClick()
        assertThat(camera.blocksPerPixel).isEqualTo(0.5f)

        composeRule.onNodeWithContentDescription("Zoom out").performClick()
        composeRule.onNodeWithContentDescription("Zoom out").performClick()
        assertThat(camera.blocksPerPixel).isEqualTo(2f)
    }

    @Test
    fun tappingBareMapSelectsThePointUnderTheFinger() {
        setScreen()
        composeRule.waitForIdle()

        tapCentre()

        val selected = userActions.single() as SeedMapAction.SelectPoint
        // The middle of the canvas is the middle of the camera, which sits on block (0, 0).
        assertThat(selected.pos.x).isIn(-2..2)
        assertThat(selected.pos.z).isIn(-2..2)
    }

    @Test
    fun tappingAPinSelectsTheStructure() {
        setScreen(SeedMapState(pins = persistentListOf(village)))
        composeRule.waitForIdle()

        tapCentre()

        assertThat(userActions).containsExactly(SeedMapAction.SelectStructure(village))
    }

    @Test
    fun tappingTheSpawnSelectsIt() {
        setScreen(SeedMapState(spawn = BlockPos(0, 0)))
        composeRule.waitForIdle()

        tapCentre()

        assertThat(userActions).containsExactly(SeedMapAction.SelectSpawn)
    }

    @Test
    fun hintsToZoomInWhilePinsAreHidden() {
        setScreen(SeedMapState(pinsHidden = true))

        composeRule.onNodeWithText("Zoom in to see structures").assertIsDisplayed()
    }

    @Test
    fun noHintWhilePinsAreShown() {
        setScreen(SeedMapState(pinsHidden = false))

        composeRule.onNodeWithText("Zoom in to see structures").assertDoesNotExist()
    }

    @Test
    fun readsOutTheCentreCoordinates() {
        camera.centerOn(BlockPos(1234, -56))
        setScreen()

        composeRule.onNodeWithText("X 1234, Z -56").assertIsDisplayed()
    }

    @Test
    fun theSpawnButtonIsOnlyInTheOverworld() {
        setScreen(SeedMapState(dimension = Dimension.OVERWORLD))
        composeRule.onNodeWithTag(GO_TO_SPAWN_TAG).performClick()
        assertThat(userActions).containsExactly(SeedMapAction.GoToSpawn)
    }

    @Test
    fun noSpawnButtonInTheNether() {
        setScreen(SeedMapState(dimension = Dimension.NETHER))

        composeRule.onNodeWithTag(GO_TO_SPAWN_TAG).assertDoesNotExist()
    }

    // endregion

    // region go to coordinates

    @Test
    fun goToCoordinatesNeedsTwoWholeNumbers() {
        setScreen()
        composeRule.onNodeWithContentDescription("Go to coordinates").performClick()

        composeRule.onNodeWithTag(GOTO_CONFIRM_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(GOTO_X_TAG).performTextInput("120")
        composeRule.onNodeWithTag(GOTO_CONFIRM_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(GOTO_Z_TAG).performTextInput("abc")
        composeRule.onNodeWithTag(GOTO_CONFIRM_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(GOTO_Z_TAG).performTextClearance()
        composeRule.onNodeWithTag(GOTO_Z_TAG).performTextInput("-45")
        composeRule.onNodeWithTag(GOTO_CONFIRM_TAG).assertIsEnabled()
    }

    @Test
    fun goingToCoordinatesSendsThemAndClosesTheDialog() {
        setScreen()
        composeRule.onNodeWithContentDescription("Go to coordinates").performClick()
        composeRule.onNodeWithTag(GOTO_X_TAG).performTextInput("120")
        composeRule.onNodeWithTag(GOTO_Z_TAG).performTextInput("-45")

        composeRule.onNodeWithTag(GOTO_CONFIRM_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.GoToCoordinates(120, -45))
        composeRule.onNodeWithTag(GOTO_CONFIRM_TAG).assertDoesNotExist()
    }

    // endregion

    // region saved seeds

    private val savedA = SavedSeed(seed = 262, version = "26.3", label = "Spawn village")
    private val savedB = SavedSeed(seed = -99, version = "1.12", label = null)

    @Test
    fun theSavedSeedsMenuListsThemByNameOrSeed() {
        setScreen(SeedMapState(savedSeeds = persistentListOf(savedA, savedB)))

        composeRule.onNodeWithTag(SAVED_SEEDS_TAG).performClick()

        composeRule.onNodeWithText("Spawn village").assertIsDisplayed()
        composeRule.onNodeWithText("Seed 262 · Java 26.3").assertIsDisplayed()
        composeRule.onNodeWithText("-99").assertIsDisplayed()
        composeRule.onNodeWithText("Seed -99 · Java 1.12").assertIsDisplayed()
    }

    @Test
    fun anEmptyMenuSaysSo() {
        setScreen()

        composeRule.onNodeWithTag(SAVED_SEEDS_TAG).performClick()

        composeRule.onNodeWithText("No saved seeds yet").assertIsDisplayed()
    }

    @Test
    fun choosingASavedSeedLoadsIt() {
        setScreen(SeedMapState(savedSeeds = persistentListOf(savedA, savedB)))
        composeRule.onNodeWithTag(SAVED_SEEDS_TAG).performClick()

        composeRule.onNodeWithText("Spawn village", useUnmergedTree = true).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.LoadSavedSeed(savedA))
    }

    @Test
    fun theTrashButtonRemovesASavedSeed() {
        setScreen(SeedMapState(savedSeeds = persistentListOf(savedA, savedB)))
        composeRule.onNodeWithTag(SAVED_SEEDS_TAG).performClick()

        composeRule.onNodeWithContentDescription("Remove Spawn village from saved seeds").performClick()

        assertThat(userActions).containsExactly(SeedMapAction.DeleteSavedSeed(savedA))
    }

    @Test
    fun savingTheCurrentSeedAsksForAnOptionalName() {
        setScreen()
        composeRule.onNodeWithTag(SAVED_SEEDS_TAG).performClick()
        composeRule.onNodeWithText("Save this seed").performClick()

        composeRule.onNodeWithTag(SAVE_NAME_TAG).performTextInput("Mushroom island")
        composeRule.onNodeWithTag(SAVE_CONFIRM_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.SaveSeed("Mushroom island"))
    }

    @Test
    fun savingWithoutANameSendsWhatWasTyped() {
        setScreen()
        composeRule.onNodeWithTag(SAVED_SEEDS_TAG).performClick()
        composeRule.onNodeWithText("Save this seed").performClick()

        composeRule.onNodeWithTag(SAVE_CONFIRM_TAG).performClick()

        // The ViewModel turns an empty name into no label.
        assertThat(userActions).containsExactly(SeedMapAction.SaveSeed(""))
    }

    @Test
    fun aSeedThatIsAlreadySavedCannotBeSavedAgain() {
        setScreen(SeedMapState(seed = 262, version = McVersion.V26_3, savedSeeds = persistentListOf(savedA)))
        composeRule.onNodeWithTag(SAVED_SEEDS_TAG).performClick()

        composeRule.onNodeWithText("This seed is saved").assertIsNotEnabled()
    }

    // endregion

    // region structure filter

    @Test
    fun theFilterSheetShowsOnlyStructuresThisVersionHas() {
        setScreen(SeedMapState(version = McVersion.V1_8))

        composeRule.onNodeWithTag(FILTER_BUTTON_TAG).performClick()

        composeRule.onNodeWithText("Village").assertIsDisplayed()
        composeRule.onNodeWithText("Ocean Monument").assertIsDisplayed()
        composeRule.onNodeWithText("Trial Chambers").assertDoesNotExist()
        composeRule.onNodeWithText("Woodland Mansion").assertDoesNotExist()
        composeRule.onNodeWithText("Ruined Portal").assertDoesNotExist()
    }

    @Test
    fun theFilterSheetOffersTheNewestStructuresInTheNewestVersion() {
        setScreen(SeedMapState(version = McVersion.newest))

        composeRule.onNodeWithTag(FILTER_BUTTON_TAG).performClick()

        composeRule.onNodeWithText("Trial Chambers").assertExists()
        composeRule.onNodeWithText("Ancient City").assertExists()
    }

    @Test
    fun tappingAChipTogglesTheStructure() {
        setScreen(SeedMapState(enabledStructures = persistentSetOf(StructureType.VILLAGE)))
        composeRule.onNodeWithTag(FILTER_BUTTON_TAG).performClick()

        composeRule.onNodeWithText("Igloo").performClick()

        assertThat(userActions).containsExactly(SeedMapAction.ToggleStructure(StructureType.IGLOO))
    }

    @Test
    fun theFilterSheetWorksWhenAVersionHasNoNetherStructures() {
        // Beta 1.7 has villages and nothing else but strongholds in the overworld; the sheet still opens.
        setScreen(SeedMapState(version = McVersion.B1_7))

        composeRule.onNodeWithTag(FILTER_BUTTON_TAG).performClick()

        composeRule.onNodeWithTag(FILTER_SHEET_TAG).assertIsDisplayed()
    }

    // endregion

    // region selection sheet

    @Test
    fun noSheetWithoutASelection() {
        setScreen()

        composeRule.onNodeWithTag(SELECTION_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun aStructureSelectionShowsItsNameAndWhereItIs() {
        setScreen(
            SeedMapState(selection = MapSelection.Structure(StructurePos(StructureType.VILLAGE, BlockPos(104, -216)))),
        )

        composeRule.onNodeWithTag(SELECTION_TITLE_TAG).assertTextEquals("Village")
        composeRule.onNodeWithText("X 104, Z -216").assertIsDisplayed()
    }

    @Test
    fun aBiomeSelectionIsNamedAfterItsWikiPage() {
        // Biome id 1 is plains.
        setScreen(SeedMapState(selection = MapSelection.Biome(BlockPos(5, 6), biomeId = 1)))

        composeRule.onNodeWithTag(SELECTION_TITLE_TAG).assertTextEquals("Plains")
    }

    @Test
    fun openWikiArticleSendsTheAction() {
        setScreen(SeedMapState(selection = MapSelection.Structure(village)))

        composeRule.onNodeWithTag(OPEN_ARTICLE_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.OpenWikiArticle)
    }

    @Test
    fun aBiomeWithNoWikiPageHasNoOpenButton() {
        setScreen(SeedMapState(selection = MapSelection.Biome(BlockPos(5, 6), biomeId = 9999)))

        composeRule.onNodeWithTag(SELECTION_TITLE_TAG).assertTextEquals("Unknown biome")
        composeRule.onNodeWithTag(OPEN_ARTICLE_TAG).assertDoesNotExist()
    }

    @Test
    fun copyCoordinatesPutsThemOnTheClipboard() {
        setScreen(
            SeedMapState(selection = MapSelection.Structure(StructurePos(StructureType.VILLAGE, BlockPos(104, -216)))),
        )

        composeRule.onNodeWithTag(COPY_COORDINATES_TAG).performClick()
        composeRule.waitForIdle()

        val clipboard =
            ApplicationProvider.getApplicationContext<Context>().getSystemService(
                ClipboardManager::class.java,
            )
        assertThat(
            clipboard.primaryClip
                ?.getItemAt(0)
                ?.text
                .toString(),
        ).isEqualTo("104, -216")
        composeRule.onNodeWithTag(COPY_COORDINATES_TAG).assertTextContains("Copied")
    }

    @Test
    fun closingTheSheetDismissesTheSelection() {
        setScreen(SeedMapState(selection = MapSelection.Spawn(BlockPos(0, 0))))

        composeRule.onNodeWithTag(SELECTION_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SELECTION_TITLE_TAG).assertTextEquals("World spawn")
    }

    // endregion
}
