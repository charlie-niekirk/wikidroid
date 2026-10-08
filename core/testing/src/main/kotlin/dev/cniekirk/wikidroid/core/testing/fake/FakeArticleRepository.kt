package dev.cniekirk.wikidroid.core.testing.fake

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.data.ArticleRepository
import dev.cniekirk.wikidroid.core.model.Article
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Serves whatever a test has pushed for a title with [emit]. Pushing again simulates the cached copy
 * followed by a refreshed one. A title nothing was pushed for emits nothing, which looks like "still loading".
 */
class FakeArticleRepository : ArticleRepository {
    private val results = mutableMapOf<String, MutableStateFlow<Result<Article, DataError>?>>()

    /** Every title passed to [getArticle], in order. */
    val requestedTitles = mutableListOf<String>()
    var clearCacheCalls = 0
        private set

    fun emit(
        title: String,
        result: Result<Article, DataError>,
    ) {
        flowFor(title).value = result
    }

    fun emit(article: Article) = emit(article.title, Result.Success(article))

    override fun getArticle(title: String): Flow<Result<Article, DataError>> {
        requestedTitles += title
        return flowFor(title).filterNotNull()
    }

    override suspend fun clearCache() {
        clearCacheCalls++
    }

    private fun flowFor(title: String) = results.getOrPut(title.replace('_', ' ')) { MutableStateFlow(null) }
}
