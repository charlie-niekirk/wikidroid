package dev.cniekirk.wikidroid.feature.explore

import dev.cniekirk.wikidroid.core.model.Category
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The categories on the Explore grid, curated by hand so the home screen is useful on first launch without
 * a request. Each name is a real category on minecraft.wiki; `ExploreCategoriesTest` guards the list's shape.
 */
val ExploreCategories: ImmutableList<Category> =
    persistentListOf(
        Category("Blocks"),
        Category("Natural blocks"),
        Category("Items"),
        Category("Hostile mobs"),
        Category("Passive mobs"),
        Category("Neutral mobs"),
        Category("Overworld biomes"),
        Category("Nether biomes"),
        Category("End biomes"),
        Category("Generated structures"),
        Category("Enchantments"),
        Category("Effects"),
        Category("Potions"),
        Category("Food"),
        Category("Tools"),
        Category("Weapons"),
        Category("Armor"),
        Category("Redstone"),
        Category("Plants"),
        Category("Commands"),
        Category("Tutorials"),
    )
