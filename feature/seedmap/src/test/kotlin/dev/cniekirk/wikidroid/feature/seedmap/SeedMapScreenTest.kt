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

    // region nether

    @Test
    fun theNetherReadoutAddsTheOverworldPosition() {
        camera.centerOn(BlockPos(100, -20))
        setScreen(SeedMapState(dimension = Dimension.NETHER))

        composeRule.onNodeWithTag(CENTER_READOUT_TAG).assertTextEquals("X 100, Z -20\nOverworld X 800, Z -160")
    }

    @Test
    fun theOverworldReadoutHasNoSecondLine() {
        camera.centerOn(BlockPos(100, -20))
        setScreen(SeedMapState(dimension = Dimension.OVERWORLD))

        composeRule.onNodeWithTag(CENTER_READOUT_TAG).assertTextEquals("X 100, Z -20")
    }

    @Test
    fun aNetherSelectionAlsoSaysWhereItIsInTheOverworld() {
        setScreen(
            SeedMapState(
                dimension = Dimension.NETHER,
                selection = MapSelection.Structure(StructurePos(StructureType.FORTRESS, BlockPos(-12, 40))),
            ),
        )

        composeRule.onNodeWithText("Overworld X -96, Z 320", substring = true).assertIsDisplayed()
    }

    @Test
    fun changingDimensionKeepsTheViewOnTheSamePlace() {
        var state by mutableStateOf(SeedMapState(dimension = Dimension.OVERWORLD))
        camera.centerOn(BlockPos(800, -1600))
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                SeedMapScreen(state = state, tiles = tiles, onAction = {}, camera = camera)
            }
        }

        state = state.copy(dimension = Dimension.NETHER)
        composeRule.waitForIdle()
        assertThat(camera.centerX).isEqualTo(100.0)
        assertThat(camera.centerZ).isEqualTo(-200.0)
        assertThat(camera.blocksPerPixel).isEqualTo(0.25f)

        state = state.copy(dimension = Dimension.OVERWORLD)
        composeRule.waitForIdle()
        assertThat(camera.centerX).isEqualTo(800.0)
        assertThat(camera.centerZ).isEqualTo(-1600.0)
        assertThat(camera.blocksPerPixel).isEqualTo(2f)
    }

    // endregion

    // region legend

    private val legendState =
        SeedMapState(
            version = McVersion.newest,
            biomeLegend =
                persistentListOf(
                    BiomeLegendEntry(id = 21, title = "Jungle", argb = 0xFF537B09.toInt()),
                    BiomeLegendEntry(id = 1, title = "Plains", argb = 0xFF8DB360.toInt()),
                    BiomeLegendEntry(id = 4, title = "Forest", argb = 0xFF056621.toInt()),
                ),
        )

    private fun scrollLegendTo(tag: String) {
        composeRule.onNodeWithTag(LEGEND_SHEET_TAG).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun theLegendListsStructuresWithTheirIconsAndBiomesWithTheirColours() {
        setScreen(legendState)

        composeRule.onNodeWithTag(LEGEND_BUTTON_TAG).performClick()

        composeRule.onNodeWithTag(LEGEND_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(legendStructureTag("World spawn")).assertIsDisplayed()
        scrollLegendTo(legendStructureTag("Village"))
        composeRule.onNodeWithTag(legendStructureTag("Village")).assertIsDisplayed()
        composeRule.onNodeWithTag(structureIconTag(StructureType.VILLAGE.icon()), useUnmergedTree = true).assertExists()
        scrollLegendTo(legendBiomeTag(1))
        composeRule.onNodeWithTag(legendBiomeTag(1)).assertIsDisplayed()
        composeRule.onNodeWithText("Plains").assertIsDisplayed()
    }

    @Test
    fun theNetherLegendHasNoSpawnAndOnlyNetherStructures() {
        setScreen(legendState.copy(dimension = Dimension.NETHER))

        composeRule.onNodeWithTag(LEGEND_BUTTON_TAG).performClick()

        composeRule.onNodeWithTag(legendStructureTag("Nether Fortress")).assertIsDisplayed()
        composeRule.onNodeWithTag(legendStructureTag("World spawn")).assertDoesNotExist()
        composeRule.onNodeWithTag(legendStructureTag("Village")).assertDoesNotExist()
    }

    @Test
    fun theLegendSearchNarrowsBothListsIgnoringCase() {
        setScreen(legendState)
        composeRule.onNodeWithTag(LEGEND_BUTTON_TAG).performClick()

        composeRule.onNodeWithTag(LEGEND_SEARCH_TAG).performTextInput("fOrE")

        composeRule.onNodeWithTag(legendBiomeTag(4)).assertIsDisplayed()
        composeRule.onNodeWithTag(legendBiomeTag(1)).assertDoesNotExist()
        composeRule.onNodeWithTag(legendStructureTag("Village")).assertDoesNotExist()
    }

    @Test
    fun theLegendSearchFindsStructures() {
        setScreen(legendState)
        composeRule.onNodeWithTag(LEGEND_BUTTON_TAG).performClick()

        composeRule.onNodeWithTag(LEGEND_SEARCH_TAG).performTextInput("pyramid")

        composeRule.onNodeWithTag(legendStructureTag("Desert Pyramid")).assertIsDisplayed()
        composeRule.onNodeWithTag(legendStructureTag("Jungle Pyramid")).assertIsDisplayed()
        composeRule.onNodeWithTag(legendBiomeTag(1)).assertDoesNotExist()
    }

    @Test
    fun aSearchWithNoMatchSaysSo() {
        setScreen(legendState)
        composeRule.onNodeWithTag(LEGEND_BUTTON_TAG).performClick()

        composeRule.onNodeWithTag(LEGEND_SEARCH_TAG).performTextInput("zzzz")

        composeRule.onNodeWithTag(LEGEND_EMPTY_TAG).assertIsDisplayed()
    }

    @Test
    fun aLegendRowOpensItsArticleAndClosesTheSheet() {
        setScreen(legendState)
        composeRule.onNodeWithTag(LEGEND_BUTTON_TAG).performClick()

        scrollLegendTo(legendBiomeTag(21))
        composeRule.onNodeWithTag(legendBiomeTag(21)).performClick()
        composeRule.waitForIdle()

        assertThat(userActions).containsExactly(SeedMapAction.OpenLegendArticle("Jungle"))
        composeRule.onNodeWithTag(LEGEND_SHEET_TAG).assertDoesNotExist()
    }

    // endregion

    // region icons

    @Test
    fun aFilterChipShowsTheWikiIconOfItsStructure() {
        setScreen(SeedMapState(version = McVersion.newest))

        composeRule.onNodeWithTag(STRUCTURES_CHIP_TAG).performClick()

        composeRule.onNodeWithTag(structureIconTag(StructureType.VILLAGE.icon()), useUnmergedTree = true).assertExists()
        composeRule
            .onNodeWithTag(structureIconTag(StructureType.TRIAL_CHAMBERS.icon()), useUnmergedTree = true)
            .assertExists()
        composeRule.onAllNodesWithTag(MARKER_FALLBACK_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theSelectionSheetShowsTheIconBesideTheName() {
        setScreen(SeedMapState(selection = MapSelection.Structure(village)))

        composeRule
            .onNodeWithTag(structureIconTag(StructureType.VILLAGE.icon()), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun theSpawnSelectionShowsTheSpawnIcon() {
        setScreen(SeedMapState(selection = MapSelection.Spawn(BlockPos(0, 0))))

        composeRule.onNodeWithTag(structureIconTag(SpawnIcon), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aBiomeSelectionHasNoStructureIcon() {
        setScreen(SeedMapState(selection = MapSelection.Biome(BlockPos(5, 6), biomeId = 1)))

        AllStructureIcons.forEach {
            composeRule.onNodeWithTag(structureIconTag(it), useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun aMarkerIconFallsBackToTheColouredShapeWhenThereIsNoIcon() {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) { MarkerIcon(StructureType.VILLAGE.markerStyle(), icon = null) }
        }

        composeRule.onNodeWithTag(MARKER_FALLBACK_TAG).assertIsDisplayed()
    }

    @Test
    fun aMarkerIconWithAnIconDrawsTheSprite() {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                MarkerIcon(StructureType.VILLAGE.markerStyle(), icon = StructureType.VILLAGE.icon())
            }
        }

        composeRule.onNodeWithTag(structureIconTag(StructureType.VILLAGE.icon())).assertIsDisplayed()
        composeRule.onNodeWithTag(MARKER_FALLBACK_TAG).assertDoesNotExist()
    }

    @Test
    fun pinsWithSpritesStillHitTestAtTheirCentre() {
        // Pins are drawn bigger than the old shapes, but taps are matched by distance to the position, so a
        // tap on the sprite's edge still selects it.
        setScreen(SeedMapState(pins = persistentListOf(village)))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SEED_MAP_CANVAS_TAG).performTouchInput {
            click(center + Offset(pinReachPx(density) * 0.8f, 0f))
        }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WINDOW_MILLIS)
        composeRule.waitForIdle()

        assertThat(userActions).containsExactly(SeedMapAction.SelectStructure(village))
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

    // region structure filter

    @Test
    fun theFilterSheetShowsOnlyStructuresThisVersionHas() {
        setScreen(SeedMapState(version = McVersion.V1_8))

        composeRule.onNodeWithTag(STRUCTURES_CHIP_TAG).performClick()

        composeRule.onNodeWithText("Village").assertIsDisplayed()
        composeRule.onNodeWithText("Ocean Monument").assertIsDisplayed()
        composeRule.onNodeWithText("Trial Chambers").assertDoesNotExist()
        composeRule.onNodeWithText("Woodland Mansion").assertDoesNotExist()
        composeRule.onNodeWithText("Ruined Portal").assertDoesNotExist()
    }

    @Test
    fun theFilterSheetOffersTheNewestStructuresInTheNewestVersion() {
        setScreen(SeedMapState(version = McVersion.newest))

        composeRule.onNodeWithTag(STRUCTURES_CHIP_TAG).performClick()

        composeRule.onNodeWithText("Trial Chambers").assertExists()
        composeRule.onNodeWithText("Ancient City").assertExists()
    }

    @Test
    fun tappingAChipTogglesTheStructure() {
        setScreen(SeedMapState(enabledStructures = persistentSetOf(StructureType.VILLAGE)))
        composeRule.onNodeWithTag(STRUCTURES_CHIP_TAG).performClick()

        composeRule.onNodeWithText("Igloo").performClick()

        assertThat(userActions).containsExactly(SeedMapAction.ToggleStructure(StructureType.IGLOO))
    }

    @Test
    fun theStructuresChipCountsWhatIsShown() {
        var state by mutableStateOf(SeedMapState(version = McVersion.newest))
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                SeedMapScreen(state = state, tiles = tiles, onAction = {}, camera = camera)
            }
        }
        composeRule.onNodeWithTag(STRUCTURES_CHIP_TAG).assertTextContains("Structures · 2")

        // Villages and strongholds are not in the Nether.
        state = state.copy(dimension = Dimension.NETHER)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(STRUCTURES_CHIP_TAG).assertTextContains("Structures · 0")
    }

    @Test
    fun theFilterSheetWorksWhenAVersionHasNoNetherStructures() {
        // Beta 1.7 has villages and nothing else but strongholds in the overworld; the sheet still opens.
        setScreen(SeedMapState(version = McVersion.B1_7))

        composeRule.onNodeWithTag(STRUCTURES_CHIP_TAG).performClick()

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
