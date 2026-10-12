package dev.cniekirk.wikidroid.core.domain.article

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.dataResultOf
import dev.cniekirk.wikidroid.core.data.ArticleRepository
import dev.cniekirk.wikidroid.core.data.LibraryRepository
import dev.cniekirk.wikidroid.core.model.Article
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Opens an article: the copies [ArticleRepository.getArticle] emits (a saved one first, then a newer revision
 * if there is one), and a visit in the history once the first copy has been shown. The flow completes when
 * the repository's does, so collecting it again is a retry.
 */
@Inject
class ObserveArticleUseCase(
    private val articles: ArticleRepository,
    private val library: LibraryRepository,
) {
    operator fun invoke(title: String): Flow<Result<Article, DataError>> =
        flow {
            var visitRecorded = false
            articles.getArticle(title).collect { result ->
                // Emit first: the visit is recorded after the collector has put the page on screen.
                emit(result)
                if (result is Result.Success && !visitRecorded) {
                    visitRecorded = true
                    // A history write that fails must not take the page down with it.
                    dataResultOf { library.recordVisit(result.data.toSummary()) }
                }
            }
        }
}
