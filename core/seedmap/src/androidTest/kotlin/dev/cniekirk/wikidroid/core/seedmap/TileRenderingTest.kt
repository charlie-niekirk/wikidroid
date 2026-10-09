package dev.cniekirk.wikidroid.core.seedmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** The renderer and cache over the real engine, so the bitmaps are the ones the map will draw. */
@RunWith(AndroidJUnit4::class)
class TileRenderingTest {
    private val real = JniSeedMapEngine(Dispatchers.Default)
    private val tileRequests = AtomicInteger()
    private val engine =
        object : SeedMapEngine by real {
            override suspend fun biomeTile(key: TileKey): BiomeTile {
                tileRequests.incrementAndGet()
                return real.biomeTile(key)
            }
        }

    private val world = MapWorld(262, McVersion.V1_18)

    @After
    fun tearDown() = runBlocking { real.shutDown() }

    private fun key(
        x: Int,
        z: Int = 0,
        scale: TileScale = TileScale.CELL_4,
    ) = TileKey(world, scale, x, z)

    @Test
    fun aRenderedTileHasOnePixelPerCellInTheBiomesColours() =
        runBlocking {
            val tile = engine.biomeTile(key(0))
            val palette = BiomePalette(engine.biomeColors())

            val bitmap = TileRenderer(engine).render(tile)

            assertThat(bitmap.width).isEqualTo(TILE_CELLS)
            assertThat(bitmap.height).isEqualTo(TILE_CELLS)
            for ((x, z) in listOf(0 to 0, 255 to 0, 0 to 255, 255 to 255, 100 to 37, 37 to 100)) {
                assertThat(bitmap.getPixel(x, z)).isEqualTo(palette.argb(tile.biomes[z * TILE_CELLS + x]))
            }
        }

    @Test
    fun theFirstAndLastPixelsMatchTheReferenceBiomes() =
        runBlocking {
            val golden = WorldGoldens.all.first { it.world == world && it.seed == 262L }
            val palette = BiomePalette(engine.biomeColors())

            val bitmap = TileRenderer(engine).render(engine.biomeTile(key(0)))

            assertThat(bitmap.getPixel(0, 0)).isEqualTo(palette.argb(golden.tile4.first))
            assertThat(bitmap.getPixel(TILE_CELLS - 1, TILE_CELLS - 1)).isEqualTo(palette.argb(golden.tile4.last))
        }

    @Test
    fun loadingATwiceGeneratesItOnce() =
        runBlocking {
            val cache = TileCache(engine, TileRenderer(engine), maxBytes = 8 * TILE_BYTES)

            val first = cache.load(key(0))
            val second = cache.load(key(0))

            assertThat(second).isSameInstanceAs(first)
            assertThat(tileRequests.get()).isEqualTo(1)
            assertThat(cache.peek(key(0))).isSameInstanceAs(first)
        }

    @Test
    fun peekingAnUnloadedTileFindsNothingAndGeneratesNothing() =
        runBlocking {
            val cache = TileCache(engine, TileRenderer(engine), maxBytes = 8 * TILE_BYTES)

            assertThat(cache.peek(key(5))).isNull()
            assertThat(tileRequests.get()).isEqualTo(0)
        }

    @Test
    fun theLeastRecentlyUsedTileIsEvictedWhenTheBudgetIsFull() =
        runBlocking {
            val cache = TileCache(engine, TileRenderer(engine), maxBytes = 2 * TILE_BYTES)

            cache.load(key(0))
            cache.load(key(1))
            cache.peek(key(0)) // touching tile 0 makes tile 1 the oldest
            cache.load(key(2))

            assertThat(cache.peek(key(0))).isNotNull()
            assertThat(cache.peek(key(1))).isNull()
            assertThat(cache.peek(key(2))).isNotNull()
        }

    @Test
    fun clearingEmptiesTheCache() =
        runBlocking {
            val cache = TileCache(engine, TileRenderer(engine), maxBytes = 4 * TILE_BYTES)
            cache.load(key(0))

            cache.clear()

            assertThat(cache.peek(key(0))).isNull()
        }

    @Test
    fun tilesOfDifferentWorldsOrScalesAreCachedSeparately() =
        runBlocking {
            val cache = TileCache(engine, TileRenderer(engine), maxBytes = 8 * TILE_BYTES)

            val a = cache.load(key(0))
            val otherScale = cache.load(key(0, scale = TileScale.CELL_16))
            val otherSeed = cache.load(TileKey(world.copy(seed = 999), TileScale.CELL_4, 0, 0))

            assertThat(otherScale).isNotSameInstanceAs(a)
            assertThat(otherSeed).isNotSameInstanceAs(a)
            assertThat(tileRequests.get()).isEqualTo(3)
        }

    private companion object {
        const val TILE_BYTES = TILE_CELLS * TILE_CELLS * 4
    }
}
