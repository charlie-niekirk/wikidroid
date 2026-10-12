package dev.cniekirk.wikidroid.core.domain.article

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.dataResultOf
import dev.cniekirk.wikidroid.core.data.ArticleRepository
import dev.zacsweers.metro.Inject

/** Deletes the saved copies of every article that isn't bookmarked. A failure is returned, not thrown. */
@Inject
class ClearArticleCacheUseCase(
    private val articles: ArticleRepository,
) {
    suspend operator fun invoke(): Result<Unit, DataError> = dataResultOf { articles.clearCache() }
}
