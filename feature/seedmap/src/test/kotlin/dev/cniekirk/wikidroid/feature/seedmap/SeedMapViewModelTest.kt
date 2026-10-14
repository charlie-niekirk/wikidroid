package dev.cniekirk.wikidroid.feature.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.domain.seedmap.BiomeAtUseCase
import dev.cniekirk.wikidroid.core.domain.seedmap.FindNearestStructureUseCase
import dev.cniekirk.wikidroid.core.domain.seedmap.FindSpawnUseCase
import dev.cniekirk.wikidroid.core.domain.seedmap.FindStrongholdsUseCase
import dev.cniekirk.wikidroid.core.domain.seedmap.FindStructuresUseCase
import dev.cniekirk.wikidroid.core.domain.seedmap.GetBiomeLegendUseCase
import dev.cniekirk.wikidroid.core.model.PoiCategory
import dev.cniekirk.wikidroid.core.model.PoiColor
import dev.cniekirk.wikidroid.core.model.PointOfInterest
import dev.cniekirk.wikidroid.core.model.SavedSeed
import dev.cniekirk.wikidroid.core.seedmap.BiomeLegendEntry
import dev.cniekirk.wikidroid.core.seedmap.BlockArea
import dev.cniekirk.wikidroid.core.seedmap.BlockPos
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.MapWorld
import dev.cniekirk.wikidroid.core.seedmap.McVersion
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine
import dev.cniekirk.wikidroid.core.seedmap.StructurePos
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.testing.MainDispatcherRule
import dev.cniekirk.wikidroid.core.testing.fake.FakePointOfInterestRepository
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

@Suppress("LargeClass") // One test class for one view model, grouped by region like the others.
class SeedMapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val engine = FakeSeedMapEngine()
    private val seeds = FakeSeedRepository()
    private val pois = FakePointOfInterestRepository()
    private val viewModel =
        SeedMapViewModel(
            seeds,
            pois,
            FindSpawnUseCase(engine),
            FindStrongholdsUseCase(engine),
            FindStructuresUseCase(engine),
            GetBiomeLegendUseCase(engine),
            BiomeAtUseCase(engine),
            FindNearestStructureUseCase(engine),
        )

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
    fun `opening the article closes the selection sheet`() =
        runTest {
            onMap {
                act(SeedMapAction.SelectStructure(pin(StructureType.VILLAGE, 1, 1)))

                act(SeedMapAction.OpenWikiArticle)
                expectEffect(SeedMapEffect.OpenArticle("Village"))

                assertThat(state.selection).isNull()
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
    fun `the nearest structure is searched from the middle of the view, turned on, selected and centred on`() =
        runTest {
            engine.structuresHandler = { _, type, area ->
                val pos = BlockPos(5300, 2000)
                if (pos.x in area.minX until area.maxX && pos.z in area.minZ until area.maxZ) {
                    listOf(StructurePos(type, pos))
                } else {
                    emptyList()
                }
            }

            onMap {
                act(SeedMapAction.ViewportChanged(viewport(centerX = 5000, centerZ = 2000)))
                engine.structureRequests.clear()

                act(SeedMapAction.GoToNearestStructure(StructureType.MANSION))

                expectEffect(SeedMapEffect.CenterOn(BlockPos(5300, 2000)))
                assertThat(
                    engine.structureRequests
                        .first()
                        .area.minX,
                ).isEqualTo(5000 - 2048)
                assertThat(state.enabledStructures).contains(StructureType.MANSION)
                assertThat(state.selection)
                    .isEqualTo(MapSelection.Structure(StructurePos(StructureType.MANSION, BlockPos(5300, 2000))))
                assertThat(state.findingNearest).isNull()
            }
        }

    @Test
    fun `the search shows it is finding until the answer comes`() =
        runTest {
            val answer = CompletableDeferred<List<BlockPos>>()
            engine.strongholdsHandler = { _, _ -> answer.await() }

            onMap {
                act(SeedMapAction.GoToNearestStructure(StructureType.STRONGHOLD))
                assertThat(state.findingNearest).isEqualTo(StructureType.STRONGHOLD)

                answer.complete(listOf(BlockPos(900, -300)))
                runCurrent()

                assertThat(state.findingNearest).isNull()
                expectEffect(SeedMapEffect.CenterOn(BlockPos(900, -300)))
            }
        }

    @Test
    fun `nothing within reach says so until the message has been shown`() =
        runTest {
            onMap {
                act(SeedMapAction.GoToNearestStructure(StructureType.ANCIENT_CITY))

                assertThat(state.nearestMissing).isEqualTo(StructureType.ANCIENT_CITY)
                assertThat(state.selection).isNull()
                assertThat(state.enabledStructures).doesNotContain(StructureType.ANCIENT_CITY)

                act(SeedMapAction.NearestMissingShown)

                assertThat(state.nearestMissing).isNull()
            }
        }

    @Test
    fun `an answer for a world the map has left is dropped`() =
        runTest {
            val answer = CompletableDeferred<List<BlockPos>>()
            engine.strongholdsHandler = { _, _ -> answer.await() }

            onMap {
                act(SeedMapAction.GoToNearestStructure(StructureType.STRONGHOLD))
                act(SeedMapAction.SubmitSeed("7"))
                answer.complete(listOf(BlockPos(900, -300)))
                runCurrent()

                assertThat(state.findingNearest).isNull()
                assertThat(state.selection).isNull()
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

    // region dimensions and the legend

    @Test
    fun `falls back to the overworld when an older version has no End`() =
        runTest {
            onMap {
                act(SeedMapAction.SelectDimension(Dimension.END))

                act(SeedMapAction.SelectVersion(McVersion.B1_7))

                assertThat(state.dimension).isEqualTo(Dimension.OVERWORLD)
                assertThat(state.version).isEqualTo(McVersion.B1_7)
            }
        }

    @Test
    fun `switching dimension drops the old pins and selection`() =
        runTest {
            engine.structuresHandler = { _, type, _ -> listOf(pin(type, 5, 5)) }
            onMap {
                act(SeedMapAction.ViewportChanged(viewport()))
                settle()
                act(SeedMapAction.SelectStructure(pin(StructureType.VILLAGE, 5, 5)))
                assertThat(state.pins).isNotEmpty()

                act(SeedMapAction.SelectDimension(Dimension.NETHER))

                assertThat(state.pins).isEmpty()
                assertThat(state.selection).isNull()
                assertThat(state.spawn).isNull()
            }
        }

    @Test
    fun `lists the biomes of the world for the legend, by wiki title with their colours`() =
        runTest {
            engine.biomesHandler = { _, _ -> listOf(21, 1, 4) }
            engine.colors = IntArray(256) { 0xFF000000.toInt() or it }

            onMap {
                assertThat(state.biomeLegend)
                    .containsExactly(
                        BiomeLegendEntry(4, "Forest", 0xFF000000.toInt() or 4),
                        BiomeLegendEntry(21, "Jungle", 0xFF000000.toInt() or 21),
                        BiomeLegendEntry(1, "Plains", 0xFF000000.toInt() or 1),
                    ).inOrder()
            }
        }

    @Test
    fun `the legend follows the version and the dimension`() =
        runTest {
            engine.biomesHandler = { version, dimension ->
                when {
                    dimension == Dimension.NETHER -> listOf(8)
                    version == McVersion.V1_12 -> listOf(1)
                    else -> listOf(1, 4)
                }
            }

            onMap {
                assertThat(state.biomeLegend.map { it.id }).containsExactly(4, 1)

                act(SeedMapAction.SelectVersion(McVersion.V1_12))
                assertThat(state.biomeLegend.map { it.id }).containsExactly(1)

                act(SeedMapAction.SelectVersion(McVersion.newest))
                act(SeedMapAction.SelectDimension(Dimension.NETHER))
                assertThat(state.biomeLegend.map { it.title }).containsExactly("Nether Wastes")
            }
        }

    @Test
    fun `a legend that arrives for an earlier world is discarded`() =
        runTest {
            val slow = CompletableDeferred<List<Int>>()
            engine.biomesHandler = { version, _ -> if (version == McVersion.V1_12) slow.await() else listOf(4) }

            onMap {
                act(SeedMapAction.SelectVersion(McVersion.V1_12))
                act(SeedMapAction.SelectVersion(McVersion.newest))
                slow.complete(listOf(1))
                runCurrent()

                assertThat(state.biomeLegend.map { it.id }).containsExactly(4)
            }
        }

    @Test
    fun `a failing biome lookup leaves the legend empty rather than crashing`() =
        runTest {
            engine.biomesHandler = { _, _ -> error("boom") }

            onMap { assertThat(state.biomeLegend).isEmpty() }
        }

    @Test
    fun `a legend row opens its article without touching the selection`() =
        runTest {
            onMap {
                act(SeedMapAction.SelectStructure(pin(StructureType.VILLAGE, 1, 1)))

                act(SeedMapAction.OpenLegendArticle("Jungle"))

                expectEffect(SeedMapEffect.OpenArticle("Jungle"))
                assertThat(state.selection).isNotNull()
            }
        }

    // endregion

    // region display

    @Test
    fun `the grid starts off and the crosshair on, and the switches change them`() =
        runTest {
            onMap {
                assertThat(state.showGrid).isFalse()
                assertThat(state.showCrosshair).isTrue()

                act(SeedMapAction.SetGrid(true))
                act(SeedMapAction.SetCrosshair(false))

                assertThat(state.showGrid).isTrue()
                assertThat(state.showCrosshair).isFalse()
            }
        }

    @Test
    fun `slime chunks start hidden and the switch shows them`() =
        runTest {
            onMap {
                assertThat(state.showSlimeChunks).isFalse()

                act(SeedMapAction.SetSlimeChunks(true))

                assertThat(state.showSlimeChunks).isTrue()
                assertThat(state.slimeChunksShown).isTrue()
            }
        }

    @Test
    fun `tapping a slime chunk says so`() =
        runTest {
            engine.biomeHandler = { _, _, _ -> 1 }

            onMap {
                // Chunk (-8, -6) of seed 262 is a slime chunk (SlimeChunksTest); chunk (0, 0) is not.
                act(SeedMapAction.SelectPoint(BlockPos(-128, -96)))
                assertThat((state.selection as MapSelection.Biome).slimeChunk).isTrue()

                act(SeedMapAction.SelectPoint(BlockPos(0, 0)))
                assertThat((state.selection as MapSelection.Biome).slimeChunk).isFalse()
            }
        }

    @Test
    fun `the Nether has no slime chunks`() =
        runTest {
            engine.biomeHandler = { _, _, _ -> 8 }

            onMap {
                act(SeedMapAction.SetSlimeChunks(true))
                act(SeedMapAction.SelectDimension(Dimension.NETHER))
                act(SeedMapAction.SelectPoint(BlockPos(-128, -96)))

                assertThat((state.selection as MapSelection.Biome).slimeChunk).isFalse()
                assertThat(state.slimeChunksShown).isFalse()
            }
        }

    // endregion

    // region points of interest

    private fun save(
        name: String = "Base",
        category: PoiCategory = PoiCategory.BASE,
        x: Int = 100,
        z: Int = -200,
        y: Int? = 64,
        note: String = "",
        color: PoiColor = category.defaultColor,
    ) = SeedMapAction.SavePoi(name, category, color, x, z, y, note)

    private fun saved(
        id: Long,
        dimension: Dimension = Dimension.OVERWORLD,
        name: String? = "Base",
        category: PoiCategory = PoiCategory.BASE,
        x: Int = 100,
        z: Int = -200,
    ) = PointOfInterest(
        id = id,
        seed = SeedMapState.DEFAULT_SEED,
        dimension = dimension.name,
        x = x,
        z = z,
        y = 64,
        name = name,
        category = category,
        createdAt = pois.now,
    )

    @Test
    fun `a long press opens the editor there and saving adds the point to this world`() =
        runTest {
            onMap {
                act(SeedMapAction.StartAddPoi(BlockPos(100, -200)))
                assertThat(state.poiEditor).isEqualTo(PoiDraft(BlockPos(100, -200)))

                act(save(note = "  Behind the waterfall "))

                assertThat(state.poiEditor).isNull()
                assertThat(pois.current).containsExactly(saved(1).copy(note = "Behind the waterfall"))
                assertThat(state.pois).containsExactly(saved(1).copy(note = "Behind the waterfall"))
            }
        }

    @Test
    fun `a blank name and note are saved as none`() =
        runTest {
            onMap {
                act(SeedMapAction.StartAddPoi(BlockPos(0, 0)))
                act(save(name = "   ", note = " ", category = PoiCategory.FARM, x = 5, z = 6, y = null))

                val point = pois.current.single()
                assertThat(point.name).isNull()
                assertThat(point.note).isNull()
                assertThat(point.y).isNull()
                assertThat(point.color).isEqualTo(PoiColor.GREEN)
            }
        }

    @Test
    fun `saving with no editor open does nothing`() =
        runTest {
            onMap {
                act(save())

                assertThat(pois.current).isEmpty()
            }
        }

    @Test
    fun `editing a point replaces it and keeps its id`() =
        runTest {
            onMap {
                act(SeedMapAction.StartAddPoi(BlockPos(100, -200)))
                act(save())
                act(SeedMapAction.SelectPoi(state.pois.single()))

                act(SeedMapAction.EditPoi(state.pois.single()))
                assertThat(state.selection).isNull()
                assertThat(state.poiEditor).isEqualTo(PoiDraft(BlockPos(100, -200), saved(1)))

                act(save(name = "Home", color = PoiColor.PINK, x = 1, z = 2))

                assertThat(
                    pois.current,
                ).containsExactly(saved(1, name = "Home", x = 1, z = 2).copy(color = PoiColor.PINK))
            }
        }

    @Test
    fun `deleting a point can be undone`() =
        runTest {
            onMap {
                act(SeedMapAction.StartAddPoi(BlockPos(100, -200)))
                act(save())
                val point = state.pois.single()
                act(SeedMapAction.SelectPoi(point))

                act(SeedMapAction.DeletePoi(point))
                assertThat(state.pois).isEmpty()
                assertThat(state.selection).isNull()
                assertThat(state.removedPoi).isEqualTo(point)

                act(SeedMapAction.UndoDeletePoi)
                assertThat(state.pois).containsExactly(point)
                assertThat(state.removedPoi).isNull()
            }
        }

    @Test
    fun `a deleted point stays deleted once its snackbar has gone`() =
        runTest {
            onMap {
                act(SeedMapAction.StartAddPoi(BlockPos(100, -200)))
                act(save())
                act(SeedMapAction.DeletePoi(state.pois.single()))

                act(SeedMapAction.RemovedPoiShown)
                act(SeedMapAction.UndoDeletePoi)

                assertThat(state.removedPoi).isNull()
                assertThat(pois.current).isEmpty()
            }
        }

    @Test
    fun `points follow the seed and dimension but not the version`() =
        runTest {
            pois.save(saved(0, dimension = Dimension.NETHER, name = "Hub"))
            pois.save(saved(0, name = "Base"))

            onMap {
                assertThat(state.pois.map { it.name }).containsExactly("Base")

                act(SeedMapAction.SelectVersion(McVersion.V1_20))
                assertThat(state.pois.map { it.name }).containsExactly("Base")

                act(SeedMapAction.SelectDimension(Dimension.NETHER))
                assertThat(state.pois.map { it.name }).containsExactly("Hub")

                act(SeedMapAction.SubmitSeed("1"))
                assertThat(state.pois).isEmpty()
            }
        }

    @Test
    fun `a portal's linked portal is added to the other dimension`() =
        runTest {
            onMap {
                act(SeedMapAction.StartAddPoi(BlockPos(100, -100)))
                act(save(name = "Hub", category = PoiCategory.PORTAL, x = 100, z = -100))

                act(SeedMapAction.SelectPoi(state.pois.single()))
                act(SeedMapAction.AddLinkedPortal(state.pois.single()))

                assertThat(state.linkedPortalAddedTo).isEqualTo(Dimension.NETHER)
                // The sheet closes, or it would hide the snackbar saying where the portal went.
                assertThat(state.selection).isNull()
                val linked = pois.current.single { it.dimension == Dimension.NETHER.name }
                assertThat(linked.name).isEqualTo("Hub")
                assertThat(linked.pos).isEqualTo(BlockPos(12, -13))
                assertThat(linked.category).isEqualTo(PoiCategory.PORTAL)

                act(SeedMapAction.LinkedPortalShown)
                assertThat(state.linkedPortalAddedTo).isNull()
            }
        }

    @Test
    fun `only a portal has a linked portal`() =
        runTest {
            onMap {
                act(SeedMapAction.StartAddPoi(BlockPos(100, -100)))
                act(save())

                act(SeedMapAction.AddLinkedPortal(state.pois.single()))

                assertThat(pois.current).hasSize(1)
                assertThat(state.linkedPortalAddedTo).isNull()
            }
        }

    @Test
    fun `going to a point selects it and centres the map on it`() =
        runTest {
            pois.save(saved(0))

            onMap {
                val point = state.pois.single()
                viewModel.onAction(SeedMapAction.GoToPoi(point))

                expectEffect(SeedMapEffect.CenterOn(BlockPos(100, -200)))
                assertThat(state.selection).isEqualTo(MapSelection.Poi(point))
            }
        }

    // endregion

    // region measure and saved seeds

    @Test
    fun `the ruler measures between two taps until it is stopped`() =
        runTest {
            onMap {
                act(SeedMapAction.SelectSpawn)
                act(SeedMapAction.StartMeasure)
                assertThat(state.measure).isEqualTo(Measurement())

                act(SeedMapAction.MeasurePoint(BlockPos(0, 0)))
                act(SeedMapAction.MeasurePoint(BlockPos(30, 40)))
                assertThat(state.measure?.blocks).isEqualTo(50L)

                act(SeedMapAction.StopMeasure)
                assertThat(state.measure).isNull()
            }
        }

    @Test
    fun `measuring from a selection starts there and closes its sheet`() =
        runTest {
            engine.biomeHandler = { _, _, _ -> 1 }

            onMap {
                act(SeedMapAction.SelectPoint(BlockPos(7, 8)))
                act(SeedMapAction.MeasureFrom(BlockPos(7, 8)))

                assertThat(state.selection).isNull()
                assertThat(state.measure).isEqualTo(Measurement(start = BlockPos(7, 8)))
            }
        }

    @Test
    fun `renaming a saved seed keeps its place and a blank name removes it`() =
        runTest {
            val first = SavedSeed(262, "26.3", "Base")
            val second = SavedSeed(1, "1.12", "Island")
            seeds.saveSeed(second)
            seeds.saveSeed(first)

            onMap {
                act(SeedMapAction.RenameSavedSeed(second, "  Spawn island "))
                act(SeedMapAction.RenameSavedSeed(first, "  "))

                assertThat(seeds.current)
                    .containsExactly(SavedSeed(262, "26.3", null), SavedSeed(1, "1.12", "Spawn island"))
                    .inOrder()
            }
        }

    @Test
    fun `point counts per seed follow the saved points`() =
        runTest {
            pois.save(saved(0))
            pois.save(saved(0, dimension = Dimension.NETHER))

            onMap {
                assertThat(state.poiCounts).containsExactly(SeedMapState.DEFAULT_SEED, 2)

                act(SeedMapAction.DeletePoi(state.pois.single()))
                assertThat(state.poiCounts).containsExactly(SeedMapState.DEFAULT_SEED, 1)
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
