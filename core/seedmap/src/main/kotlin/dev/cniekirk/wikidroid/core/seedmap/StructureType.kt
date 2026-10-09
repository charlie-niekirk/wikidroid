package dev.cniekirk.wikidroid.core.seedmap

/**
 * A structure the map can place, with where and since when it generates. [nativeId] is cubiomes'
 * `StructureType` value and [nativeName] what its `struct2str` prints; an instrumented test checks both,
 * and that [isAvailableIn] agrees with the native tables for every version.
 *
 * [STRONGHOLD] is listed so it can be toggled like the rest, but it is not region based: ask for it with
 * [SeedMapEngine.strongholds], not [SeedMapEngine.structuresIn].
 */
enum class StructureType(
    internal val nativeId: Int,
    internal val nativeName: String,
    val dimension: Dimension,
    private val since: McVersion,
) {
    DESERT_PYRAMID(1, "desert_pyramid", Dimension.OVERWORLD, McVersion.V1_3),
    JUNGLE_TEMPLE(2, "jungle_pyramid", Dimension.OVERWORLD, McVersion.V1_3),
    SWAMP_HUT(3, "swamp_hut", Dimension.OVERWORLD, McVersion.V1_4),
    IGLOO(4, "igloo", Dimension.OVERWORLD, McVersion.V1_9),
    VILLAGE(5, "village", Dimension.OVERWORLD, McVersion.B1_8),
    OCEAN_RUIN(6, "ocean_ruin", Dimension.OVERWORLD, McVersion.V1_13),
    SHIPWRECK(7, "shipwreck", Dimension.OVERWORLD, McVersion.V1_13),
    MONUMENT(8, "monument", Dimension.OVERWORLD, McVersion.V1_8),
    MANSION(9, "mansion", Dimension.OVERWORLD, McVersion.V1_11),
    OUTPOST(10, "pillager_outpost", Dimension.OVERWORLD, McVersion.V1_14),
    RUINED_PORTAL(11, "ruined_portal", Dimension.OVERWORLD, McVersion.V1_16_1),
    RUINED_PORTAL_NETHER(12, "ruined_portal_nether", Dimension.NETHER, McVersion.V1_16_1),
    ANCIENT_CITY(13, "ancient_city", Dimension.OVERWORLD, McVersion.V1_19_2),
    BURIED_TREASURE(14, "buried_treasure", Dimension.OVERWORLD, McVersion.V1_13),
    FORTRESS(18, "fortress", Dimension.NETHER, McVersion.V1_0),
    BASTION(19, "bastion_remnant", Dimension.NETHER, McVersion.V1_16_1),
    NETHER_FOSSIL(20, "nether_fossil", Dimension.NETHER, McVersion.V1_16_1),
    END_CITY(21, "end_city", Dimension.END, McVersion.V1_9),
    END_GATEWAY(22, "end_gateway", Dimension.END, McVersion.V1_13),
    TRAIL_RUINS(24, "trail_ruins", Dimension.OVERWORLD, McVersion.V1_20),
    TRIAL_CHAMBERS(25, "trial_chambers", Dimension.OVERWORLD, McVersion.V1_21_1),
    ABANDONED_CAMP(26, "abandoned_camp", Dimension.OVERWORLD, McVersion.V26_3),
    STRONGHOLD(27, "stronghold", Dimension.OVERWORLD, McVersion.B1_8),
    ;

    /** Whether [version] generates this structure, in the dimension it belongs to. */
    fun isAvailableIn(version: McVersion): Boolean = version >= since && version.supports(dimension)

    /** Whether [world] is in the right dimension and version for this structure to exist there. */
    fun isAvailableIn(world: MapWorld): Boolean = world.dimension == dimension && isAvailableIn(world.version)

    /** Whether [SeedMapEngine.structuresIn] can find it by region (everything but [STRONGHOLD]). */
    val isRegionBased: Boolean get() = this != STRONGHOLD

    companion object {
        /** The structures a [world] can show, in declaration order. */
        fun availableIn(world: MapWorld): List<StructureType> = entries.filter { it.isAvailableIn(world) }
    }
}
