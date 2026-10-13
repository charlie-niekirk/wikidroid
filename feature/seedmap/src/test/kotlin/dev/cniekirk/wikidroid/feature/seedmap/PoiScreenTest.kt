package dev.cniekirk.wikidroid.feature.seedmap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.PoiCategory
import dev.cniekirk.wikidroid.core.model.PoiColor
import dev.cniekirk.wikidroid.core.model.PointOfInterest
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.TILE_CELLS
import dev.cniekirk.wikidroid.core.seedmap.TileCache
import dev.cniekirk.wikidroid.core.seedmap.TileRenderer
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import dev.cniekirk.wikidroid.core.testing.fake.FakeSeedMapEngine
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

/** The player's points: adding by long press, the editor, the point's sheet, the list and undo. */
class PoiScreenTest : ComposeTest() {
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

    /**
     * Holds a finger on the middle of the map past the long-press timeout. Injected events do not move the test
     * clock, so the clock is advanced while the finger is down.
     */
    private fun longPressCentre() {
        val canvas = composeRule.onNodeWithTag(SEED_MAP_CANVAS_TAG)
        canvas.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(LONG_HOLD_MILLIS)
        canvas.performTouchInput { up() }
        composeRule.waitForIdle()
    }

    private val base =
        PointOfInterest(
            id = 4,
            seed = SeedMapState.DEFAULT_SEED,
            dimension = "OVERWORLD",
            x = 100,
            z = -200,
            y = 64,
            name = "Base",
            category = PoiCategory.BASE,
            note = "Behind the waterfall",
        )
    private val portal =
        base.copy(
            id = 5,
            name = "Hub",
            category = PoiCategory.PORTAL,
            color = PoiColor.PURPLE,
            y = null,
        )

    // region the map

    @Test
    fun aLongPressOnTheMapStartsAPointThere() {
        setScreen()

        longPressCentre()

        assertThat(userActions).containsExactly(SeedMapAction.StartAddPoi(BlockPos(0, 0)))
    }

    @Test
    fun aTwoFingerHoldIsNotALongPress() {
        setScreen()

        val canvas = composeRule.onNodeWithTag(SEED_MAP_CANVAS_TAG)
        canvas.performTouchInput {
            down(0, center)
            down(1, center + Offset(50f, 0f))
        }
        composeRule.mainClock.advanceTimeBy(LONG_HOLD_MILLIS)
        canvas.performTouchInput {
            up(1)
            up(0)
        }
        composeRule.waitForIdle()

        assertThat(userActions.filterIsInstance<SeedMapAction.StartAddPoi>()).isEmpty()
    }

    @Test
    fun aLongPressOnAPointEditsIt() {
        camera.centerOn(base.pos)
        setScreen(SeedMapState(pois = persistentListOf(base)))

        longPressCentre()

        assertThat(userActions).containsExactly(SeedMapAction.EditPoi(base))
    }

    // endregion

    // region editor

    @Test
    fun theEditorStartsAtTheLongPressAndSavesWhatWasEntered() {
        setScreen(SeedMapState(poiEditor = PoiDraft(BlockPos(120, -340))))

        composeRule.onNodeWithTag(POI_X_TAG).assertTextContains("120")
        composeRule.onNodeWithTag(POI_Z_TAG).assertTextContains("-340")
        composeRule.onNodeWithTag(POI_NAME_TAG).performTextInput("Home")
        composeRule.onNodeWithTag(poiCategoryTag(PoiCategory.FARM)).performScrollTo().performClick()
        composeRule.onNodeWithTag(POI_Y_TAG).performScrollTo().performTextInput("70")
        composeRule.onNodeWithTag(POI_NOTE_TAG).performScrollTo().performTextInput("Wheat")
        composeRule.onNodeWithTag(POI_SAVE_TAG).performScrollTo().performClick()

        assertThat(userActions).containsExactly(
            SeedMapAction.SavePoi("Home", PoiCategory.FARM, PoiColor.GREEN, x = 120, z = -340, y = 70, note = "Wheat"),
        )
    }

    @Test
    fun aPickedColourStaysWhenTheKindChanges() {
        setScreen(SeedMapState(poiEditor = PoiDraft(BlockPos(0, 0))))

        composeRule.onNodeWithTag(poiColorTag(PoiColor.PINK)).performScrollTo().performClick()
        composeRule.onNodeWithTag(poiCategoryTag(PoiCategory.MINE)).performScrollTo().performClick()
        composeRule.onNodeWithTag(poiColorTag(PoiColor.PINK)).assertIsSelected()
        composeRule.onNodeWithTag(POI_SAVE_TAG).performScrollTo().performClick()

        val saved = userActions.single() as SeedMapAction.SavePoi
        assertThat(saved.color).isEqualTo(PoiColor.PINK)
        assertThat(saved.category).isEqualTo(PoiCategory.MINE)
    }

    @Test
    fun saveWaitsForWholeNumberCoordinates() {
        setScreen(SeedMapState(poiEditor = PoiDraft(BlockPos(0, 0))))

        composeRule.onNodeWithTag(POI_X_TAG).performTextClearance()
        composeRule.onNodeWithTag(POI_SAVE_TAG).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(POI_X_TAG).performTextInput("-12")
        composeRule.onNodeWithTag(POI_Y_TAG).performScrollTo().performTextInput("high")
        composeRule.onNodeWithTag(POI_SAVE_TAG).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(POI_Y_TAG).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(POI_SAVE_TAG).performScrollTo().assertIsEnabled()
    }

    @Test
    fun editingAPointFillsInItsDetailsAndOffersDelete() {
        setScreen(SeedMapState(poiEditor = PoiDraft(base.pos, base)))

        composeRule.onNodeWithText("Edit point").assertIsDisplayed()
        composeRule.onNodeWithTag(POI_NAME_TAG).assertTextContains("Base")
        composeRule.onNodeWithTag(POI_DELETE_TAG).performScrollTo().performClick()

        assertThat(userActions).containsExactly(SeedMapAction.DeletePoi(base))
    }

    // endregion

    // region the point's sheet

    @Test
    fun aSelectedPointShowsItsDetailsAndCanBeEditedOrDeleted() {
        setScreen(SeedMapState(pois = persistentListOf(base), selection = MapSelection.Poi(base)))

        composeRule.onNodeWithTag(SELECTION_TITLE_TAG).assertTextContains("Base")
        composeRule.onNodeWithText("Y 64").assertIsDisplayed()
        composeRule.onNodeWithText("Behind the waterfall").assertIsDisplayed()
        composeRule.onNodeWithTag(OPEN_ARTICLE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(POI_ADD_LINKED_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(POI_EDIT_TAG).performClick()
        composeRule.onNodeWithTag(POI_SHEET_DELETE_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.EditPoi(base), SeedMapAction.DeletePoi(base)).inOrder()
    }

    @Test
    fun aPortalSaysWhereItLeadsAndCanAddThatPortal() {
        setScreen(SeedMapState(pois = persistentListOf(portal), selection = MapSelection.Poi(portal)))

        composeRule.onNodeWithTag(POI_LINKED_TAG).assertTextContains("Links to X 12, Z -25 in the Nether")
        composeRule.onNodeWithTag(POI_ADD_LINKED_TAG).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.AddLinkedPortal(portal))
    }

    @Test
    fun anUnnamedPointIsCalledByItsKind() {
        val unnamed = base.copy(name = null)
        setScreen(SeedMapState(pois = persistentListOf(unnamed), selection = MapSelection.Poi(unnamed)))

        composeRule.onNodeWithTag(SELECTION_TITLE_TAG).assertTextContains("Base")
    }

    @Test
    fun aSlimeChunkSaysSo() {
        setScreen(SeedMapState(selection = MapSelection.Biome(BlockPos(-128, -96), 1, slimeChunk = true)))

        composeRule.onNodeWithTag(SLIME_CHUNK_TAG).assertTextContains("Slime chunk (chunk -8, -6)")
    }

    // endregion

    // region list and snackbars

    @Test
    fun thePointsChipCountsThePointsAndListsThem() {
        setScreen(SeedMapState(pois = persistentListOf(base, portal)))

        composeRule.onNodeWithTag(POIS_CHIP_TAG).assertTextContains("Points · 2").performClick()
        composeRule.onNodeWithTag(POI_LIST_TAG).performScrollToNode(hasTestTag(poiRowTag(portal)))
        composeRule.onNodeWithTag(poiRowTag(portal)).performClick()

        assertThat(userActions).containsExactly(SeedMapAction.GoToPoi(portal))
        composeRule.onNodeWithTag(POI_LIST_TAG).assertDoesNotExist()
    }

    @Test
    fun anEmptyListSaysHowToAddAPoint() {
        setScreen()

        composeRule.onNodeWithTag(POIS_CHIP_TAG).performClick()

        composeRule.onNodeWithTag(POI_LIST_EMPTY_TAG).assertIsDisplayed()
    }

    @Test
    fun aDeletedPointCanBeUndoneFromTheSnackbar() {
        setScreen(SeedMapState(removedPoi = base))

        composeRule.onNodeWithText("Deleted Base").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertThat(userActions).containsExactly(SeedMapAction.UndoDeletePoi)
    }

    @Test
    fun aLinkedPortalSaysWhereItWent() {
        setScreen(SeedMapState(linkedPortalAddedTo = dev.cniekirk.wikidroid.core.seedmap.Dimension.NETHER))

        composeRule.onNodeWithText("Linked portal added to the Nether").assertIsDisplayed()
    }

    // endregion

    private companion object {
        const val LONG_HOLD_MILLIS = 1_000L
    }
}
