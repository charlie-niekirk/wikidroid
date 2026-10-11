package dev.cniekirk.wikidroid.feature.explore

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Category
import dev.cniekirk.wikidroid.core.model.CategoryMember
import dev.cniekirk.wikidroid.core.model.Paged
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class CategoryDetailStateTest {
    private val loaded =
        CategoryDetailState(
            title = "Hostile mobs",
            phase = CategoryPhase.Loaded,
            subcategories = persistentListOf(Category("Undead mobs")),
            pages = persistentListOf(ArticleSummary(title = "Creeper")),
            continuation = "next",
            isLoadingMore = true,
        )

    @Test
    fun aLaterBatchAppendsWithoutRepeatsAndTakesItsToken() {
        val batch =
            Paged(
                persistentListOf(
                    CategoryMember.Subcategory(Category("Undead mobs")),
                    CategoryMember.Page(ArticleSummary(title = "Creeper")),
                    CategoryMember.Page(ArticleSummary(title = "Zombie")),
                ),
                continuation = null,
            )

        val next = loaded.withNextBatch(Result.Success(batch))

        assertThat(next.subcategories).containsExactly(Category("Undead mobs"))
        assertThat(next.pages.map { it.title }).containsExactly("Creeper", "Zombie").inOrder()
        assertThat(next.continuation).isNull()
        assertThat(next.isLoadingMore).isFalse()
    }

    @Test
    fun aFailedLaterBatchKeepsWhatIsShown() {
        val next = loaded.withNextBatch(Result.Failure(DataError.Network()))

        assertThat(next).isEqualTo(loaded.copy(isLoadingMore = false, loadMoreFailed = true))
    }

    @Test
    fun aFailedFirstBatchReportsWhy() {
        val initial = CategoryDetailState(title = "Hostile mobs")

        assertThat(initial.withFirstBatch(Result.Failure(DataError.Network())).phase)
            .isEqualTo(CategoryPhase.Failed(DataError.Network()))
    }
}
