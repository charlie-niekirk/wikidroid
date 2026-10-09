package dev.cniekirk.wikidroid.core.seedmap

/** Hash of a tile's biome ids with `h = 31 * h + id` over the cells in row order, plus its first and last cell. */
class TileGolden(
    val first: Int,
    val last: Int,
    val hash: Int,
)

/**
 * What the pinned cubiomes commit (`4f04235`) produces for one world, from the same throwaway host program
 * as [SeedMapEngineGoldenTest] (built from `seedmap_core.c`, so it runs the same code as the JNI layer,
 * with `clang -O1 -fwrapv` on the Mac). They catch JNI, ABI and build-flag mistakes, and a tile or
 * structure query that drifts from the core, but not a bug in cubiomes itself: none of this was checked
 * against Chunkbase.
 *
 * - [strongholds]: the first three, nearest ring first.
 * - [villages]: found in blocks `[0, 1536) x [0, 1536)`.
 * - [buriedTreasure]: found in `[-512, 512) x [-512, 512)`; null where the version has none (before 1.13).
 * - [tile4], [tile16]: the biome tile at tile (0, 0) of that scale, sea level.
 */
class WorldGolden(
    val seed: Long,
    val version: McVersion,
    val strongholds: List<BlockPos>,
    val villages: List<BlockPos>,
    val buriedTreasure: List<BlockPos>?,
    val tile4: TileGolden,
    val tile16: TileGolden,
) {
    val world = MapWorld(seed, version)
}

object WorldGoldens {
    val all: List<WorldGolden> =
        listOf(
            WorldGolden(
                seed = 262L,
                version = McVersion.V1_12,
                strongholds = listOf(BlockPos(4, -1516), BlockPos(1940, 1252), BlockPos(-1916, 1044)),
                villages = listOf(BlockPos(368, 1072)),
                buriedTreasure = null,
                tile4 = TileGolden(first = 6, last = 1, hash = -1404022381),
                tile16 = TileGolden(first = 6, last = 24, hash = -798414756),
            ),
            WorldGolden(
                seed = 262L,
                version = McVersion.V1_18,
                strongholds = listOf(BlockPos(68, -1644), BlockPos(1844, 1204), BlockPos(-2092, 1140)),
                villages = listOf(BlockPos(1488, 1152)),
                buriedTreasure = listOf(BlockPos(393, -343)),
                tile4 = TileGolden(first = 14, last = 30, hash = -880553997),
                tile16 = TileGolden(first = 14, last = 29, hash = 1083617630),
            ),
            WorldGolden(
                seed = 262L,
                version = McVersion.V26_3,
                strongholds = listOf(BlockPos(-12, -1708), BlockPos(2036, 1220), BlockPos(-1772, 836)),
                villages = listOf(BlockPos(1488, 1152)),
                buriedTreasure = listOf(BlockPos(393, -343)),
                tile4 = TileGolden(first = 14, last = 30, hash = 651356535),
                tile16 = TileGolden(first = 14, last = 29, hash = -1673358966),
            ),
            WorldGolden(
                seed = -4172144997902289642L,
                version = McVersion.V1_12,
                strongholds = listOf(BlockPos(-556, 1508), BlockPos(-1004, -1436), BlockPos(1892, -28)),
                villages = listOf(),
                buriedTreasure = null,
                tile4 = TileGolden(first = 12, last = 13, hash = 25795323),
                tile16 = TileGolden(first = 12, last = 0, hash = 1224476853),
            ),
            WorldGolden(
                seed = -4172144997902289642L,
                version = McVersion.V1_18,
                strongholds = listOf(BlockPos(-556, 1492), BlockPos(-860, -1516), BlockPos(2452, -108)),
                villages = listOf(BlockPos(1104, 384)),
                buriedTreasure = listOf(BlockPos(-407, -439), BlockPos(25, -87)),
                tile4 = TileGolden(first = 45, last = 48, hash = 1799140284),
                tile16 = TileGolden(first = 45, last = 0, hash = -1553600052),
            ),
            WorldGolden(
                seed = -4172144997902289642L,
                version = McVersion.V26_3,
                strongholds = listOf(BlockPos(-604, 1508), BlockPos(-1260, -1852), BlockPos(2020, -220)),
                villages = listOf(BlockPos(1104, 384)),
                buriedTreasure = listOf(BlockPos(-407, -439), BlockPos(25, -87)),
                tile4 = TileGolden(first = 45, last = 48, hash = -1196631697),
                tile16 = TileGolden(first = 45, last = 0, hash = -1705234674),
            ),
            WorldGolden(
                seed = 1L,
                version = McVersion.V1_12,
                strongholds = listOf(BlockPos(-220, -1916), BlockPos(1604, 772), BlockPos(-1548, 1188)),
                villages = listOf(BlockPos(1392, 768), BlockPos(352, 1152)),
                buriedTreasure = null,
                tile4 = TileGolden(first = 0, last = 4, hash = 1214227135),
                tile16 = TileGolden(first = 0, last = 18, hash = 621595989),
            ),
            WorldGolden(
                seed = 1L,
                version = McVersion.V1_18,
                strongholds = listOf(BlockPos(-188, -2012), BlockPos(2020, 756), BlockPos(-1484, 1092)),
                villages = listOf(BlockPos(640, 816)),
                buriedTreasure = listOf(BlockPos(-439, -343)),
                tile4 = TileGolden(first = 24, last = 155, hash = 1719399724),
                tile16 = TileGolden(first = 24, last = 177, hash = -1485154801),
            ),
            WorldGolden(
                seed = 1L,
                version = McVersion.V26_3,
                strongholds = listOf(BlockPos(-140, -1804), BlockPos(1732, 708), BlockPos(-1132, 852)),
                villages = listOf(BlockPos(640, 816)),
                buriedTreasure = listOf(BlockPos(-439, -343)),
                tile4 = TileGolden(first = 24, last = 155, hash = 138340873),
                tile16 = TileGolden(first = 24, last = 185, hash = 383126608),
            ),
        )
}
