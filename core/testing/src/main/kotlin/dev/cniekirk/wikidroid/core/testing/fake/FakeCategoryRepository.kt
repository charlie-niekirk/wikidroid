package dev.cniekirk.wikidroid.core.testing.fake

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.data.CategoryRepository
import dev.cniekirk.wikidroid.core.model.Category
import dev.cniekirk.wikidroid.core.model.CategoryMember
import dev.cniekirk.wikidroid.core.model.Paged
import kotlinx.collections.immutable.persistentListOf

/** Answers from [handler], which tests replace to script batches per category and continuation. */
class FakeCategoryRepository : CategoryRepository {
    var handler: suspend (category: Category, continuation: String?) -> Result<Paged<CategoryMember>, DataError> =
        { _, _ -> Result.Success(Paged(persistentListOf(), continuation = null)) }

    val requests = mutableListOf<Pair<Category, String?>>()

    override suspend fun getMembers(
        category: Category,
        continuation: String?,
    ): Result<Paged<CategoryMember>, DataError> {
        requests += category to continuation
        return handler(category, continuation)
    }
}
