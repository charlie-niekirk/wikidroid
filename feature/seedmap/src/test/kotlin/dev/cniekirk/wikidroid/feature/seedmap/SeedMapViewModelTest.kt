package dev.cniekirk.wikidroid.feature.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.cniekirk.wikidroid.core.seedmap.StructurePos
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.testing.MainDispatcherRule
import dev.cniekirk.wikidroid.core.testing.fake.FakeSeedMapEngine
import dev.cniekirk.wikidroid.core.testing.fake.FakeSeedRepository
import dev.cniekirk.wikidroid.core.testing.fake.StructureRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.orbitmvi.orbit.test.Item
import org.orbitmvi.orbit.test.OrbitTestContext
import org.orbitmvi.orbit.test.test

class SeedMapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val engine = FakeSeedMapEngine()
    private val seeds = FakeSeedRepository()
    private val viewModel = SeedMapViewModel(engine, seeds)

    private val state get() = viewModel.container.stateFlow.value

    private val defaultWorld = MapWorld(SeedMapState.DEFAULT_SEED, McVersion.newest)

    private suspend fun TestScope.onMap(
        body: suspend OrbitTestContext<SeedMapState, SeedMapEffect, SeedMapViewModel>.() -> Unit,
    ) {
        viewModel.test(this) {
            runOnCreate()
            runCurrent()
            body()
            cancelAndIgnoreRemainingItems()
        }
    }

    /** The next side effect, passing over the state changes that came before it. */
    private suspend fun OrbitTestContext<SeedMapState, SeedMapEffect, SeedMapViewModel>.expectEffect(
        expected: SeedMapEffect,
    ) {
        while (true) {
            when (val item = awaitItem()) {
                is Item.SideEffectItem -> {
                    assertThat(item.value).isEqualTo(expected)
                    return
                }

                is Item.StateItem -> {
                    continue
                }
            }
        }
    }

    private fun TestScope.act(action: SeedMapAction) {
        viewModel.onAction(action)
        runCurrent()
    }

    /** A 1000 x 2000 px canvas at 1 block per pixel, whose pin search area is x -1024..1024, z -2048..2048. */
    private fun viewport(
        centerX: Int = 0,
        centerZ: Int = 0,
        blocksPerPixel: Float = 1f,
    ) = MapViewport(centerX, centerZ, blocksPerPixel, widthPx = 1000, heightPx = 2000)

    private val searchArea = BlockArea(-1024, -2048, 1024, 2048)

    private fun pin(
        type: StructureType,
        x: Int,
        z: Int,
    ) = StructurePos(type, BlockPos(x, z))

    private fun TestScope.settle() {
        advanceTimeBy(DEBOUNCE_MILLIS)
        runCurrent()
    }

    // region the first screen

    @Test
    fun `starts on the default world with villages and strongholds toggled on`() =
        runTest {
            onMap {
                assertThat(state.seed).isEqualTo(SeedMapState.DEFAULT_SEED)
                assertThat(state.version).isEqualTo(McVersion.newest)
                assertThat(state.dimension).isEqualTo(Dimension.OVERWORLD)
                assertThat(state.enabledStructures).containsExactly(StructureType.VILLAGE, StructureType.STRONGHOLD)
                assertThat(state.selection).isNull()
            }
        }

    @Test
    fun `finds the spawn and strongholds of the world without waiting for a view`() =
        runTest {
            engine.spawnHandler = { BlockPos(-544, -432) }
            engine.strongholdsHandler = { _, _ -> listOf(BlockPos(-12, -1708), BlockPos(2036, 1220)) }

            onMap {
                assertThat(state.spawn).isEqualTo(BlockPos(-544, -432))
                assertThat(state.strongholds).containsExactly(BlockPos(-12, -1708), BlockPos(2036, 1220)).inOrder()
                assertThat(engine.strongholdRequests).containsExactly(defaultWorld to SeedMapEngine.MAX_STRONGHOLDS)
            }
        }

    @Test
    fun `no structure search happens until the canvas has a size`() =
        runTest {
            onMap {
                settle()

                assertThat(engine.structureRequests).isEmpty()
                assertThat(state.pins).isEmpty()
            }
        }

    // endregion

    // region pins follow the view

    @Test
    fun `pins appear once the view has settled`() =
        runTest {
            engine.structuresHandler = { _, type, _ -> listOf(pin(type, 100, -200)) }

            onMap {
                act(SeedMapAction.ViewportChanged(viewport()))
                advanceTimeBy(DEBOUNCE_MILLIS - 1)
                runCurrent()
                assertThat(engine.structureRequests).isEmpty()

                advanceTimeBy(1)
                runCurrent()

                assertThat(
                    engine.structureRequests,
                ).containsExactly(StructureRequest(defaultWorld, StructureType.VILLAGE, searchArea))
                assertThat(state.pins).containsExactly(pin(StructureType.VILLAGE, 100, -200))
                assertThat(state.pinsHidden).isFalse()
            }
        }

    @Test
    fun `a burst of panning searches once, for where it ended`() =
        runTest {
            onMap {
                act(SeedMapAction.ViewportChanged(viewport(centerX = 0)))
                advanceTimeBy(100)
                act(SeedMapAction.ViewportChanged(viewport(centerX = 3000)))
                advanceTimeBy(100)
                act(SeedMapAction.ViewportChanged(viewport(centerX = 6000)))
                settle()

                // Centred on x = 6000 the screen runs 5500..6500; padded and snapped that is 4608..7168 -> 4096..7168.
                assertThat(engine.structureRequests.map { it.area })
                    .containsExactly(BlockArea(4096, -2048, 7168, 2048))
            }
        }

    @Test
    fun `panning within one search area asks nothing new`() =
        runTest {
            onMap {
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()
                engine.structureRequests.clear()

                act(SeedMapAction.ViewportChanged(viewport(centerX = 10, centerZ = 5)))
                settle()

                assertThat(engine.structureRequests).isEmpty()
            }
        }

    @Test
    fun `every toggled type is searched over the same area`() =
        runTest {
            onMap {
                act(SeedMapAction.ToggleStructure(StructureType.MONUMENT))
                act(SeedMapAction.ToggleStructure(StructureType.IGLOO))
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()
                engine.structureRequests.clear()
                act(SeedMapAction.ViewportChanged(viewport(centerX = 5000)))
                settle()

                assertThat(engine.structureRequests.map { it.type })
                    .containsExactly(StructureType.VILLAGE, StructureType.MONUMENT, StructureType.IGLOO)
                assertThat(engine.structureRequests.map { it.area }.toSet()).hasSize(1)
            }
        }

    @Test
    fun `strongholds are never searched by region`() =
        runTest {
            onMap {
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()

                assertThat(engine.structureRequests.map { it.type }).doesNotContain(StructureType.STRONGHOLD)
            }
        }

    @Test
    fun `a view too wide to search hides the pins instead`() =
        runTest {
            engine.structuresHandler = { _, type, _ -> listOf(pin(type, 1, 1)) }

            onMap {
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()
                assertThat(state.pins).isNotEmpty()
                engine.structureRequests.clear()

                act(SeedMapAction.ViewportChanged(viewport(blocksPerPixel = 32f)))
                settle()

                assertThat(engine.structureRequests).isEmpty()
                assertThat(state.pins).isEmpty()
                assertThat(state.pinsHidden).isTrue()
            }
        }

    @Test
    fun `zooming back in finds the pins again`() =
        runTest {
            engine.structuresHandler = { _, type, _ -> listOf(pin(type, 1, 1)) }

            onMap {
                act(SeedMapAction.ViewportChanged(viewport(blocksPerPixel = 32f)))
                settle()
                assertThat(state.pinsHidden).isTrue()

                act(SeedMapAction.ViewportChanged(viewport()))
                settle()

                assertThat(state.pinsHidden).isFalse()
                assertThat(state.pins).hasSize(1)
            }
        }

    // endregion

    // region toggles

    @Test
    fun `toggling a type off removes its pins at once and keeps the others`() =
        runTest {
            engine.structuresHandler = { _, type, _ -> listOf(pin(type, 10, 10)) }

            onMap {
                act(SeedMapAction.ToggleStructure(StructureType.MONUMENT))
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()
                assertThat(state.pins.map { it.type }).containsExactly(StructureType.VILLAGE, StructureType.MONUMENT)

                act(SeedMapAction.ToggleStructure(StructureType.VILLAGE))

                assertThat(state.pins.map { it.type }).containsExactly(StructureType.MONUMENT)
                assertThat(state.enabledStructures).doesNotContain(StructureType.VILLAGE)
            }
        }

    @Test
    fun `toggling a type on searches for it straight away`() =
        runTest {
            engine.structuresHandler = { _, type, _ -> listOf(pin(type, 10, 10)) }

            onMap {
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()
                engine.structureRequests.clear()

                act(SeedMapAction.ToggleStructure(StructureType.MANSION))

                assertThat(engine.structureRequests.map { it.type }).contains(StructureType.MANSION)
                assertThat(state.pins.map { it.type }).contains(StructureType.MANSION)
            }
        }

    @Test
    fun `toggling strongholds off clears them and toggling on finds them again`() =
        runTest {
            engine.strongholdsHandler = { _, _ -> listOf(BlockPos(4, 4)) }

            onMap {
                assertThat(state.strongholds).hasSize(1)

                act(SeedMapAction.ToggleStructure(StructureType.STRONGHOLD))
                assertThat(state.strongholds).isEmpty()

                act(SeedMapAction.ToggleStructure(StructureType.STRONGHOLD))
                assertThat(state.strongholds).containsExactly(BlockPos(4, 4))
                assertThat(engine.strongholdRequests).hasSize(2)
            }
        }

    @Test
    fun `toggling a type off drops a selection on one of its pins`() =
        runTest {
            val village = pin(StructureType.VILLAGE, 10, 10)

            onMap {
                act(SeedMapAction.SelectStructure(village))
                assertThat(state.selection).isEqualTo(MapSelection.Structure(village))

                act(SeedMapAction.ToggleStructure(StructureType.VILLAGE))

                assertThat(state.selection).isNull()
            }
        }

    @Test
    fun `types the version does not have are never searched even when toggled on`() =
        runTest {
            onMap {
                act(SeedMapAction.SelectVersion(McVersion.V1_12))
                act(SeedMapAction.ToggleStructure(StructureType.ANCIENT_CITY))
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()

                assertThat(engine.structureRequests.map { it.type }).containsExactly(StructureType.VILLAGE)
            }
        }

    // endregion

    // region changing the world

    @Test
    fun `submitting a number sets that seed`() =
        runTest {
            onMap {
                act(SeedMapAction.SubmitSeed("  -4172144997902289642 "))

                assertThat(state.seed).isEqualTo(-4172144997902289642L)
            }
        }

    @Test
    fun `submitting text uses the hash the game uses`() =
        runTest {
            onMap {
                act(SeedMapAction.SubmitSeed("glacier"))

                assertThat(state.seed).isEqualTo("glacier".hashCode().toLong())
            }
        }

    @Test
    fun `submitting nothing picks a seed`() =
        runTest {
            onMap {
                act(SeedMapAction.SubmitSeed(""))

                assertThat(state.seed).isNotEqualTo(SeedMapState.DEFAULT_SEED)
            }
        }

    @Test
    fun `a new seed drops the old pins, spawn and selection at once and searches the new world`() =
        runTest {
            engine.spawnHandler = { world -> BlockPos(world.seed.toInt(), 0) }
            engine.structuresHandler = { world, type, _ -> listOf(pin(type, world.seed.toInt(), 7)) }

            onMap {
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()
                act(SeedMapAction.SelectSpawn)
                assertThat(state.spawn).isEqualTo(BlockPos(262, 0))
                assertThat(state.pins).containsExactly(pin(StructureType.VILLAGE, 262, 7))
                assertThat(state.selection).isNotNull()

                engine.spawnHandler = { suspendForever() }
                engine.structuresHandler = { _, _, _ -> suspendForever() }
                act(SeedMapAction.SubmitSeed("999"))

                assertThat(state.pins).isEmpty()
                assertThat(state.spawn).isNull()
                assertThat(state.strongholds).isEmpty()
                assertThat(state.selection).isNull()
                // The new world is searched without waiting for the view to settle.
                assertThat(
                    engine.structureRequests
                        .last()
                        .world.seed,
                ).isEqualTo(999)
            }
        }

    @Test
    fun `submitting the seed already shown changes nothing`() =
        runTest {
            engine.structuresHandler = { _, type, _ -> listOf(pin(type, 1, 1)) }

            onMap {
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()
                val before = state

                act(SeedMapAction.SubmitSeed(SeedMapState.DEFAULT_SEED.toString()))

                assertThat(state).isEqualTo(before)
            }
        }

    @Test
    fun `an answer for the previous seed is thrown away`() =
        runTest {
            val slowSearch = CompletableDeferred<List<StructurePos>>()
            engine.structuresHandler = { world, type, _ ->
                if (world.seed == SeedMapState.DEFAULT_SEED) slowSearch.await() else listOf(pin(type, 5, 5))
            }

            onMap {
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()
                act(SeedMapAction.SubmitSeed("999"))
                assertThat(state.pins).containsExactly(pin(StructureType.VILLAGE, 5, 5))

                slowSearch.complete(listOf(pin(StructureType.VILLAGE, -1, -1)))
                runCurrent()

                assertThat(state.pins).containsExactly(pin(StructureType.VILLAGE, 5, 5))
            }
        }

    @Test
    fun `a new version keeps the seed and falls back to the overworld when it lacks the dimension`() =
        runTest {
            onMap {
                act(SeedMapAction.SelectDimension(Dimension.NETHER))
                assertThat(state.dimension).isEqualTo(Dimension.NETHER)

                act(SeedMapAction.SelectVersion(McVersion.V1_12))

                assertThat(state.version).isEqualTo(McVersion.V1_12)
                assertThat(state.seed).isEqualTo(SeedMapState.DEFAULT_SEED)
                assertThat(state.dimension).isEqualTo(Dimension.OVERWORLD)
            }
        }

    @Test
    fun `a new version keeps the dimension when it exists there`() =
        runTest {
            onMap {
                act(SeedMapAction.SelectDimension(Dimension.END))

                act(SeedMapAction.SelectVersion(McVersion.V1_21_4))

                assertThat(state.dimension).isEqualTo(Dimension.END)
            }
        }

    @Test
    fun `a dimension the version does not have is ignored`() =
        runTest {
            onMap {
                act(SeedMapAction.SelectVersion(McVersion.V1_12))

                act(SeedMapAction.SelectDimension(Dimension.NETHER))

                assertThat(state.dimension).isEqualTo(Dimension.OVERWORLD)
            }
        }

    @Test
    fun `the nether has no spawn and searches only nether structures`() =
        runTest {
            engine.spawnHandler = { BlockPos(1, 1) }

            onMap {
                act(SeedMapAction.ToggleStructure(StructureType.FORTRESS))
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()
                engine.spawnRequests.clear()
                engine.structureRequests.clear()

                act(SeedMapAction.SelectDimension(Dimension.NETHER))

                assertThat(state.spawn).isNull()
                assertThat(engine.spawnRequests).isEmpty()
                assertThat(engine.structureRequests.map { it.type }).containsExactly(StructureType.FORTRESS)
                assertThat(
                    engine.strongholdRequests
                        .last()
                        .first.dimension,
                ).isEqualTo(Dimension.OVERWORLD)
            }
        }

    @Test
    fun `versions without strongholds are not asked for them`() =
        runTest {
            onMap {
                engine.strongholdRequests.clear()

                act(SeedMapAction.SelectVersion(McVersion.B1_7))

                assertThat(engine.strongholdRequests).isEmpty()
                assertThat(state.strongholds).isEmpty()
            }
        }

    // endregion

    // region saved seeds

    @Test
    fun `saving keeps the current seed and version`() =
        runTest {
            onMap {
                act(SeedMapAction.SubmitSeed("1234"))
                act(SeedMapAction.SelectVersion(McVersion.V1_21_4))
                assertThat(state.isSaved).isFalse()

                act(SeedMapAction.SaveSeed("Cherry base"))

                assertThat(seeds.current).containsExactly(SavedSeed(1234, "1.21.4", "Cherry base"))
                assertThat(state.savedSeeds).containsExactly(SavedSeed(1234, "1.21.4", "Cherry base"))
                assertThat(state.isSaved).isTrue()
            }
        }

    @Test
    fun `a blank label is saved as no label`() =
        runTest {
            onMap {
                act(SeedMapAction.SaveSeed("   "))

                assertThat(seeds.current.single().label).isNull()
            }
        }

    @Test
    fun `the same seed in another version is not saved yet`() =
        runTest {
            onMap {
                act(SeedMapAction.SaveSeed())

                act(SeedMapAction.SelectVersion(McVersion.V1_12))

                assertThat(state.isSaved).isFalse()
            }
        }

    @Test
    fun `deleting a saved seed removes it`() =
        runTest {
            val saved = SavedSeed(5, "1.12", "Old")
            seeds.saveSeed(saved)
            seeds.saveSeed(SavedSeed(6, "1.12"))

            onMap {
                act(SeedMapAction.DeleteSavedSeed(saved))

                assertThat(state.savedSeeds).containsExactly(SavedSeed(6, "1.12"))
            }
        }

    @Test
    fun `loading a saved seed switches seed and version`() =
        runTest {
            onMap {
                act(SeedMapAction.LoadSavedSeed(SavedSeed(-77, "1.18")))

                assertThat(state.world).isEqualTo(MapWorld(-77, McVersion.V1_18))
            }
        }

    @Test
    fun `loading a seed saved for a version this build lacks does nothing`() =
        runTest {
            onMap {
                val before = state

                act(SeedMapAction.LoadSavedSeed(SavedSeed(-77, "99.9")))

                assertThat(state).isEqualTo(before)
            }
        }

    @Test
    fun `the saved list follows the repository`() =
        runTest {
            seeds.saveSeed(SavedSeed(1, "1.12"))

            onMap {
                assertThat(state.savedSeeds).containsExactly(SavedSeed(1, "1.12"))

                seeds.saveSeed(SavedSeed(2, "26.3"))
                runCurrent()

                assertThat(state.savedSeeds.map { it.seed }).containsExactly(2L, 1L).inOrder()
            }
        }

    // endregion

    // region selection

    @Test
    fun `tapping the map selects the biome there and links its article`() =
        runTest {
            engine.biomeHandler = { _, _, _ -> 29 }

            onMap {
                act(SeedMapAction.SelectPoint(BlockPos(120, -340)))

                assertThat(state.selection).isEqualTo(MapSelection.Biome(BlockPos(120, -340), 29))
                assertThat(state.selection?.wikiTitle).isEqualTo("Dark Forest")
                assertThat(engine.biomeRequests).containsExactly(Triple(defaultWorld, 120, -340))
            }
        }

    @Test
    fun `tapping where the generator has no answer selects nothing`() =
        runTest {
            engine.biomeHandler = { _, _, _ -> null }

            onMap {
                act(SeedMapAction.SelectPoint(BlockPos(0, 0)))

                assertThat(state.selection).isNull()
            }
        }

    @Test
    fun `a biome answer that arrives after the seed changed is thrown away`() =
        runTest {
            val slow = CompletableDeferred<Int?>()
            engine.biomeHandler = { _, _, _ -> slow.await() }

            onMap {
                act(SeedMapAction.SelectPoint(BlockPos(0, 0)))
                act(SeedMapAction.SubmitSeed("999"))

                slow.complete(4)
                runCurrent()

                assertThat(state.selection).isNull()
            }
        }

    @Test
    fun `tapping a pin selects the structure and links its article`() =
        runTest {
            val monument = pin(StructureType.MONUMENT, 64, 64)

            onMap {
                act(SeedMapAction.SelectStructure(monument))

                assertThat(state.selection).isEqualTo(MapSelection.Structure(monument))
                assertThat(state.selection?.wikiTitle).isEqualTo("Ocean Monument")
            }
        }

    @Test
    fun `the spawn can be selected once it is known`() =
        runTest {
            engine.spawnHandler = { BlockPos(-544, -432) }

            onMap {
                act(SeedMapAction.SelectSpawn)

                assertThat(state.selection).isEqualTo(MapSelection.Spawn(BlockPos(-544, -432)))
                assertThat(state.selection?.wikiTitle).isEqualTo("World spawn")
            }
        }

    @Test
    fun `dismissing clears the selection`() =
        runTest {
            onMap {
                act(SeedMapAction.SelectStructure(pin(StructureType.VILLAGE, 1, 1)))

                act(SeedMapAction.DismissSelection)

                assertThat(state.selection).isNull()
            }
        }

    @Test
    fun `opening the article asks to open the selection's wiki page`() =
        runTest {
            onMap {
                act(SeedMapAction.SelectStructure(pin(StructureType.VILLAGE, 1, 1)))

                act(SeedMapAction.OpenWikiArticle)

                expectEffect(SeedMapEffect.OpenArticle("Village"))
            }
        }

    @Test
    fun `opening the article with nothing selected does nothing`() =
        runTest {
            onMap {
                act(SeedMapAction.OpenWikiArticle)
                act(SeedMapAction.GoToCoordinates(1, 2))

                // The only effect is the one for the coordinates, so nothing was posted before it.
                expectEffect(SeedMapEffect.CenterOn(BlockPos(1, 2)))
            }
        }

    // endregion

    // region going places

    @Test
    fun `going to the spawn centres the map on it`() =
        runTest {
            engine.spawnHandler = { BlockPos(-544, -432) }

            onMap {
                act(SeedMapAction.GoToSpawn)

                expectEffect(SeedMapEffect.CenterOn(BlockPos(-544, -432)))
            }
        }

    @Test
    fun `there is nowhere to go in a dimension without a spawn`() =
        runTest {
            onMap {
                act(SeedMapAction.SelectDimension(Dimension.END))
                act(SeedMapAction.GoToSpawn)
                act(SeedMapAction.GoToCoordinates(7, 8))

                expectEffect(SeedMapEffect.CenterOn(BlockPos(7, 8)))
            }
        }

    @Test
    fun `coordinates past the world border are pulled back to it`() =
        runTest {
            val limit = SeedMapEngine.MAX_COORDINATE

            onMap {
                act(SeedMapAction.GoToCoordinates(Int.MAX_VALUE, Int.MIN_VALUE))

                expectEffect(SeedMapEffect.CenterOn(BlockPos(limit, -limit)))
            }
        }

    // endregion

    // region failures

    @Test
    fun `an engine failure leaves the map without pins instead of crashing`() =
        runTest {
            engine.structuresHandler = { _, _, _ -> error("native search failed") }
            engine.spawnHandler = { throw IllegalArgumentException("refused") }

            onMap {
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()

                assertThat(state.pins).isEmpty()
                assertThat(state.spawn).isNull()
            }
        }

    @Test
    fun `one type failing does not hide the others`() =
        runTest {
            engine.structuresHandler = { _, type, _ ->
                if (type == StructureType.MONUMENT) error("boom") else listOf(pin(type, 3, 3))
            }

            onMap {
                act(SeedMapAction.ToggleStructure(StructureType.MONUMENT))
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()

                assertThat(state.pins).containsExactly(pin(StructureType.VILLAGE, 3, 3))
            }
        }

    // endregion

    private suspend fun suspendForever(): Nothing {
        CompletableDeferred<Nothing>().await()
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 200L
    }
}
