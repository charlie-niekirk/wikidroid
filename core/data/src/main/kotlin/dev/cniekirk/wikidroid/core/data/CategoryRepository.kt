package dev.cniekirk.wikidroid.core.data

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.map
import dev.cniekirk.wikidroid.core.model.Category
import dev.cniekirk.wikidroid.core.model.CategoryMember
import dev.cniekirk.wikidroid.core.model.Paged
import dev.cniekirk.wikidroid.core.network.WikiRemoteDataSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.collections.immutable.toImmutableList

interface CategoryRepository {
    /**
     * One batch of a category's subcategories and pages. Within a batch the subcategories come first, then
     * the pages, each sorted by title; the batches themselves follow the server's order. A category that
     * doesn't exist, or is empty, yields an empty batch.
     */
    suspend fun getMembers(
        category: Category,
        continuation: String? = null,
    ): Result<Paged<CategoryMember>, DataError>
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class WikiCategoryRepository(
    private val remote: WikiRemoteDataSource,
) : CategoryRepository {
    override suspend fun getMembers(
        category: Category,
        continuation: String?,
    ): Result<Paged<CategoryMember>, DataError> =
        remote.categoryMembers(category.pageTitle, continuation).map { list ->
            Paged(
                list.pages
                    .map { it.toMember() }
                    .sortedWith(categoryMemberOrder)
                    .toImmutableList(),
                list.continuation,
            )
        }
}
