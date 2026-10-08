package dev.cniekirk.wikidroid.feature.explore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExploreCategoriesTest {
    @Test
    fun hasCategoriesToBrowse() {
        assertThat(ExploreCategories).isNotEmpty()
    }

    @Test
    fun namesAreUnique() {
        assertThat(ExploreCategories.map { it.name }).containsNoDuplicates()
    }

    @Test
    fun namesAreTheWikisOwnSpellingWithoutPrefixOrUnderscores() {
        ExploreCategories.forEach { category ->
            assertThat(category.name).doesNotContain("Category:")
            assertThat(category.name).doesNotContain("_")
            assertThat(category.name).isEqualTo(category.name.trim())
            // Used as the screen's title, so it should read as it was written.
            assertThat(category.displayName).isEqualTo(category.name)
        }
    }

    @Test
    fun coversTheCoreOfTheGame() {
        assertThat(ExploreCategories.map { it.name })
            .containsAtLeast("Blocks", "Items", "Hostile mobs", "Enchantments", "Redstone", "Tutorials")
    }
}
