package dev.cniekirk.wikidroid.core.seedmap

/** The minecraft.wiki article for each structure, under the title the wiki currently uses. */
object StructureWikiTitles {
    fun titleFor(type: StructureType): String =
        when (type) {
            StructureType.DESERT_PYRAMID -> "Desert Pyramid"
            StructureType.JUNGLE_TEMPLE -> "Jungle Pyramid"
            StructureType.SWAMP_HUT -> "Swamp Hut"
            StructureType.IGLOO -> "Igloo"
            StructureType.VILLAGE -> "Village"
            StructureType.OCEAN_RUIN -> "Ocean Ruins"
            StructureType.SHIPWRECK -> "Shipwreck"
            StructureType.MONUMENT -> "Ocean Monument"
            StructureType.MANSION -> "Woodland Mansion"
            StructureType.OUTPOST -> "Pillager Outpost"
            StructureType.RUINED_PORTAL, StructureType.RUINED_PORTAL_NETHER -> "Ruined Portal"
            StructureType.ANCIENT_CITY -> "Ancient City"
            StructureType.BURIED_TREASURE -> "Buried Treasure"
            StructureType.FORTRESS -> "Nether Fortress"
            StructureType.BASTION -> "Bastion Remnant"
            StructureType.NETHER_FOSSIL -> "Nether Fossil"
            StructureType.END_CITY -> "End City"
            StructureType.END_GATEWAY -> "End Gateway"
            StructureType.TRAIL_RUINS -> "Trail Ruins"
            StructureType.TRIAL_CHAMBERS -> "Trial Chambers"
            StructureType.ABANDONED_CAMP -> "Abandoned Camp"
            StructureType.STRONGHOLD -> "Stronghold"
        }
}
