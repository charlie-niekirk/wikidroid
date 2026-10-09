package dev.cniekirk.wikidroid.core.seedmap

import dev.cniekirk.wikidroid.core.common.DefaultDispatcher
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The engine over `libseedmap.so`. Queries run on a [GeneratorPool], so several tiles can be generated at
 * once, each on its own native generator.
 *
 * The native library is loaded on the first call, so building the graph costs nothing.
 */
@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class JniSeedMapEngine(
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : SeedMapEngine {
    private val pool = GeneratorPool(dispatcher)

    @Volatile private var colors: IntArray? = null

    override suspend fun spawn(world: MapWorld): BlockPos {
        require(world.dimension == Dimension.OVERWORLD) { "Only the overworld has a spawn point" }
        return pool.use(world) { handle ->
            val spawn = checkNotNull(NativeSeedMap.nativeGetSpawn(handle)) { "Native spawn lookup failed" }
            BlockPos(spawn[0], spawn[1])
        }
    }

    override suspend fun biomeAt(
        world: MapWorld,
        x: Int,
        z: Int,
        y: Int,
    ): Int? {
        val limit = SeedMapEngine.MAX_COORDINATE
        require(x in -limit..limit && z in -limit..limit) { "($x, $z) is outside the world border" }
        require(y in SeedMapEngine.MIN_Y..SeedMapEngine.MAX_Y) { "Height $y is outside the world" }
        return pool.use(world) { handle ->
            NativeSeedMap.nativeGetBiomeAt(handle, BLOCK_SCALE, x, y, z).takeIf { it != NO_BIOME }
        }
    }

    override suspend fun biomeTile(key: TileKey): BiomeTile =
        pool.use(key.world) { handle ->
            val biomes =
                NativeSeedMap.nativeGenBiomes(
                    handle = handle,
                    scale = key.scale.blocksPerCell,
                    x = key.tileX * TILE_CELLS,
                    z = key.tileZ * TILE_CELLS,
                    width = TILE_CELLS,
                    height = TILE_CELLS,
                    y = SeedMapEngine.SEA_LEVEL shr QUART_SHIFT,
                )
            BiomeTile(key, checkNotNull(biomes) { "Native tile generation failed for $key" })
        }

    override suspend fun structuresIn(
        world: MapWorld,
        type: StructureType,
        area: BlockArea,
    ): List<StructurePos> {
        require(type.isRegionBased) { "$type is not found by region; use strongholds()" }
        require(type.isAvailableIn(world)) {
            "$type does not generate in the ${world.dimension} of Java Edition ${world.version.label}"
        }
        val limit = SeedMapEngine.MAX_COORDINATE
        require(listOf(area.minX, area.minZ, area.maxX, area.maxZ).all { abs(it) <= limit }) {
            "$area is outside the world border"
        }
        val span = SeedMapEngine.MAX_STRUCTURE_SPAN
        require(area.width <= span && area.height <= span) { "$area is wider than $span blocks" }

        return pool.use(world) { handle ->
            val packed =
                checkNotNull(
                    NativeSeedMap.nativeStructures(handle, type.nativeId, area.minX, area.minZ, area.maxX, area.maxZ),
                ) { "Native structure search failed for $type in $area" }
            List(packed.size / PAIR) { StructurePos(type, BlockPos(packed[it * PAIR], packed[it * PAIR + 1])) }
        }
    }

    override suspend fun strongholds(
        world: MapWorld,
        count: Int,
    ): List<BlockPos> {
        require(world.dimension == Dimension.OVERWORLD) { "Only the overworld has strongholds" }
        require(count in 1..SeedMapEngine.MAX_STRONGHOLDS) {
            "Ask for 1..${SeedMapEngine.MAX_STRONGHOLDS} strongholds, not $count"
        }
        return pool.use(world) { handle ->
            val packed =
                checkNotNull(NativeSeedMap.nativeStrongholds(handle, count)) { "Native stronghold search failed" }
            List(packed.size / PAIR) { BlockPos(packed[it * PAIR], packed[it * PAIR + 1]) }
        }
    }

    override suspend fun biomeColors(): IntArray =
        (colors ?: withContext(dispatcher) { NativeSeedMap.nativeBiomeColors() }.also { colors = it }).copyOf()

    /** Frees the native generators once running queries finish. The app never calls this; tests do. */
    suspend fun shutDown() = pool.shutDown()

    private companion object {
        const val BLOCK_SCALE = 1
        const val NO_BIOME = -1
        const val PAIR = 2

        /** Heights at scales above 1 are in 4-block units. */
        const val QUART_SHIFT = 2
    }
}
