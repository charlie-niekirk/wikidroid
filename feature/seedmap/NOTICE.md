# Seed map artwork

The PNGs in `src/main/res/drawable-nodpi/` (`structure_*.png` and `dimension_*.png`) are item and block
inventory sprites (`File:Invicon_<Name>.png`) from [minecraft.wiki](https://minecraft.wiki), downloaded on
**2026-10-10**. The artwork is Mojang's; the wiki shares it under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/), which is why WikiDroid carries no
ads or purchases. About credits them next to the content licence.

| Resource | Wiki sprite |
|---|---|
| `structure_desert_pyramid` | Chiseled Sandstone |
| `structure_jungle_temple` | Mossy Cobblestone |
| `structure_swamp_hut` | Dark Oak Log |
| `structure_igloo` | Snow Block |
| `structure_village` | Bell |
| `structure_ocean_ruin` | Prismarine Bricks |
| `structure_shipwreck` | Oak Boat |
| `structure_monument` | Dark Prismarine |
| `structure_mansion` | Totem of Undying |
| `structure_outpost` | Crossbow |
| `structure_ruined_portal` (both dimensions) | Crying Obsidian |
| `structure_ancient_city` | Reinforced Deepslate |
| `structure_buried_treasure` | Chest |
| `structure_trail_ruins` | Suspicious Gravel |
| `structure_trial_chambers` | Trial Spawner |
| `structure_abandoned_camp` | Cobweb |
| `structure_fortress` | Nether Bricks |
| `structure_bastion` | Polished Blackstone Bricks |
| `structure_nether_fossil` | Bone Block |
| `structure_end_city` | Purpur Block |
| `structure_end_gateway` | End Gateway |
| `structure_stronghold` | Eye of Ender |
| `structure_spawn` | Red Bed |
| `dimension_overworld` | Grass Block |
| `dimension_nether` | Nether Wart |
| `dimension_end` | Ender Pearl |

Which structure wears which sprite is decided in one place, `StructureIcons.kt`. To refresh the sprites,
re-download them from the wiki (check each file name with `action=query&prop=imageinfo&titles=File:…` first),
keep them PNG, and update the date above. "Prismarine" and "Sea Lantern" are animated GIFs on the wiki and
cannot be used as they are.
