package dev.cniekirk.wikidroid.core.seedmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Biome tiles, structures and strongholds from the real `libseedmap.so` against [WorldGoldens] and a few
 * more values from the same host program. See [WorldGolden] for where the numbers come from.
 */
@RunWith(AndroidJUnit4::class)
class EngineQueriesGoldenTest {
    private val engine = JniSeedMapEngine(Dispatchers.Default)

    @After
    fun tearDown() = runBlocking { engine.shutDown() }

    private fun checksum(biomes: IntArray): Int = biomes.fold(0) { hash, id -> 31 * hash + id }

    private suspend fun assertTile(
        world: MapWorld,
        scale: TileScale,
        tileX: Int,
        tileZ: Int,
        golden: TileGolden,
    ) {
        val tile = engine.biomeTile(TileKey(world, scale, tileX, tileZ))

        assertThat(tile.biomes).hasLength(TILE_CELLS * TILE_CELLS)
        assertThat(tile.biomes.first()).isEqualTo(golden.first)
        assertThat(tile.biomes.last()).isEqualTo(golden.last)
        assertThat(checksum(tile.biomes)).isEqualTo(golden.hash)
    }

    @Test
    fun biomeTilesMatchTheReferenceBuild() =
        runBlocking {
            for (golden in WorldGoldens.all) {
                assertTile(golden.world, TileScale.CELL_4, 0, 0, golden.tile4)
                assertTile(golden.world, TileScale.CELL_16, 0, 0, golden.tile16)
            }
        }

    @Test
    fun aTileWestAndNorthOfTheOriginMatchesTheReferenceBuild() =
        runBlocking {
            val world = MapWorld(262, McVersion.V26_3)

            assertTile(world, TileScale.CELL_16, -1, -2, TileGolden(first = 4, last = 4, hash = -59_592_586))
        }

    @Test
    fun aTileAgreesWithSinglePointLookups() =
        runBlocking {
            for (version in listOf(McVersion.V1_12, McVersion.V1_18, McVersion.V26_3)) {
                val world = MapWorld(262, version)
                val tile = engine.biomeTile(TileKey(world, TileScale.CELL_4, 1, -1))
                val handle = NativeSeedMap.nativeCreate(version.nativeId, 0)
                try {
                    NativeSeedMap.nativeApplySeed(handle, Dimension.OVERWORLD.nativeId, world.seed)
                    // At scale 4 a cell is one biome coordinate, so the tile and the point query ask the same question.
                    for ((cx, cz) in listOf(0 to 0, 17 to 200, 255 to 255, 128 to 3, 3 to 128)) {
                        val point = NativeSeedMap.nativeGetBiomeAt(handle, 4, TILE_CELLS + cx, 15, -TILE_CELLS + cz)

                        assertThat(tile.biomes[cz * TILE_CELLS + cx]).isEqualTo(point)
                    }
                } finally {
                    NativeSeedMap.nativeDestroy(handle)
                }
            }
        }

    @Test
    fun strongholdsMatchTheReferenceBuild() =
        runBlocking {
            for (golden in WorldGoldens.all) {
                assertThat(engine.strongholds(golden.world, 3)).containsExactlyElementsIn(golden.strongholds).inOrder()
            }
        }

    @Test
    fun askingForMoreStrongholdsKeepsTheNearestOnesFirst() =
        runBlocking {
            val golden = WorldGoldens.all.first { it.version == McVersion.V26_3 }

            val all = engine.strongholds(golden.world)

            assertThat(all).hasSize(SeedMapEngine.MAX_STRONGHOLDS)
            assertThat(all.take(3)).containsExactlyElementsIn(golden.strongholds).inOrder()
            assertThat(all.toSet()).hasSize(SeedMapEngine.MAX_STRONGHOLDS)
        }

    @Test
    fun theNumberOfStrongholdsDependsOnTheVersion() =
        runBlocking {
            assertThat(engine.strongholds(MapWorld(262, McVersion.B1_7))).isEmpty()
            assertThat(engine.strongholds(MapWorld(262, McVersion.B1_8))).hasSize(3)
            assertThat(engine.strongholds(MapWorld(262, McVersion.V1_8))).hasSize(3)
            assertThat(engine.strongholds(MapWorld(262, McVersion.V1_9))).hasSize(SeedMapEngine.MAX_STRONGHOLDS)
        }

    @Test
    fun villagesMatchTheReferenceBuild() =
        runBlocking {
            val area = BlockArea(0, 0, 1536, 1536)

            for (golden in WorldGoldens.all) {
                val found = engine.structuresIn(golden.world, StructureType.VILLAGE, area)

                assertThat(found.map { it.pos }).containsExactlyElementsIn(golden.villages)
                assertThat(found.all { it.type == StructureType.VILLAGE }).isTrue()
            }
        }

    @Test
    fun buriedTreasureMatchesTheReferenceBuildAndDoesNotExistBefore113() =
        runBlocking {
            val area = BlockArea(-512, -512, 512, 512)

            for (golden in WorldGoldens.all) {
                val expected = golden.buriedTreasure
                if (expected == null) {
                    assertThat(StructureType.BURIED_TREASURE.isAvailableIn(golden.version)).isFalse()
                } else {
                    val found = engine.structuresIn(golden.world, StructureType.BURIED_TREASURE, area)
                    assertThat(found.map { it.pos }).containsExactlyElementsIn(expected)
                }
            }
        }

    @Test
    fun splittingAnAreaFindsTheSameStructures() =
        runBlocking {
            val world = MapWorld(262, McVersion.V1_18)
            val whole = BlockArea(-2048, -2048, 2048, 2048)
            val quarters =
                listOf(
                    BlockArea(-2048, -2048, 0, 0),
                    BlockArea(0, -2048, 2048, 0),
                    BlockArea(-2048, 0, 0, 2048),
                    BlockArea(0, 0, 2048, 2048),
                )

            for (type in listOf(
                StructureType.VILLAGE,
                StructureType.MONUMENT,
                StructureType.BURIED_TREASURE,
                StructureType.RUINED_PORTAL,
            )) {
                val together = engine.structuresIn(world, type, whole)
                val apart = quarters.flatMap { engine.structuresIn(world, type, it) }

                assertThat(apart).containsExactlyElementsIn(together)
                assertThat(together.all { it.pos in whole }).isTrue()
            }
        }

    @Test
    fun theNetherMatchesTheReferenceBuild() =
        runBlocking {
            val world = MapWorld(262, McVersion.V26_3, Dimension.NETHER)
            val area = BlockArea(-1024, -1024, 1024, 1024)

            val fortresses = engine.structuresIn(world, StructureType.FORTRESS, area)
            val bastions = engine.structuresIn(world, StructureType.BASTION, area)
            val fossils = engine.structuresIn(world, StructureType.NETHER_FOSSIL, BlockArea(-512, -512, 512, 512))

            assertThat(fortresses).hasSize(11)
            assertThat(fortresses.first().pos).isEqualTo(BlockPos(208, -992))
            assertThat(bastions).hasSize(13)
            assertThat(bastions.first().pos).isEqualTo(BlockPos(-304, -992))
            assertThat(fossils).hasSize(314)
            assertTile(world, TileScale.CELL_4, 0, 0, TileGolden(first = 171, last = 171, hash = -1_476_027_618))
        }

    @Test
    fun theEndMatchesTheReferenceBuild() =
        runBlocking {
            val world = MapWorld(262, McVersion.V26_3, Dimension.END)
            val area = BlockArea(-2048, -2048, 2048, 2048)

            val cities = engine.structuresIn(world, StructureType.END_CITY, area)
            val gateways = engine.structuresIn(world, StructureType.END_GATEWAY, area)

            assertThat(cities).hasSize(62)
            assertThat(cities.first().pos).isEqualTo(BlockPos(-1552, -1856))
            assertThat(gateways).hasSize(10)
            assertThat(gateways.first().pos).isEqualTo(BlockPos(1847, -2026))
            assertTile(world, TileScale.CELL_4, 0, 0, TileGolden(first = 9, last = 41, hash = -1_565_365_248))
        }

    @Test
    fun theSpawnAndPalettePairUpWithTiles() =
        runBlocking {
            val colors = engine.biomeColors()

            assertThat(colors).hasLength(256)
            assertThat(colors.all { (it ushr 24) == 0xFF }).isTrue()
            // ocean, plains and desert, from cubiomes' initBiomeColors.
            assertThat(colors[0]).isEqualTo(0xFF000070.toInt())
            assertThat(colors[1]).isEqualTo(0xFF8DB360.toInt())
            assertThat(colors[2]).isEqualTo(0xFFFA9418.toInt())
        }

    @Test
    fun theEngineRejectsWhatItCannotAnswer() =
        runBlocking {
            val world = MapWorld(262, McVersion.V1_12)
            val area = BlockArea(0, 0, 512, 512)

            assertThrows<IllegalArgumentException> { engine.structuresIn(world, StructureType.STRONGHOLD, area) }
            assertThrows<IllegalArgumentException> { engine.structuresIn(world, StructureType.OCEAN_RUIN, area) }
            assertThrows<IllegalArgumentException> { engine.structuresIn(world, StructureType.FORTRESS, area) }
            assertThrows<IllegalArgumentException> {
                engine.structuresIn(
                    world,
                    StructureType.VILLAGE,
                    BlockArea(0, 0, SeedMapEngine.MAX_STRUCTURE_SPAN + 1, 10),
                )
            }
            assertThrows<IllegalArgumentException> {
                engine.structuresIn(world, StructureType.VILLAGE, BlockArea(0, 0, 10, SeedMapEngine.MAX_COORDINATE + 1))
            }
            assertThrows<IllegalArgumentException> { engine.strongholds(world, 0) }
            assertThrows<IllegalArgumentException> { engine.strongholds(world, SeedMapEngine.MAX_STRONGHOLDS + 1) }
            assertThrows<IllegalArgumentException> {
                engine.strongholds(
                    MapWorld(1, McVersion.V26_3, Dimension.NETHER),
                    3,
                )
            }
        }

    @Test
    fun theWidestAllowedSearchAnswersForTheDensestStructure() =
        runBlocking {
            val world = MapWorld(262, McVersion.V26_3, Dimension.NETHER)
            val span = SeedMapEngine.MAX_STRUCTURE_SPAN

            val fossils = engine.structuresIn(world, StructureType.NETHER_FOSSIL, BlockArea(0, 0, span, span))

            assertThat(fossils).isNotEmpty()
        }

    private suspend inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        val thrown = runCatching { block() }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(T::class.java)
    }
}
