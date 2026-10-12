package dev.cniekirk.wikidroid.core.domain.article

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.data.ArticleRepository
import dev.cniekirk.wikidroid.core.testing.fake.FakeArticleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ClearArticleCacheUseCaseTest {
    @Test
    fun clearsTheCache() =
        runTest {
            val articles = FakeArticleRepository()

            val outcome = ClearArticleCacheUseCase(articles)()

            assertThat(outcome).isEqualTo(Result.Success(Unit))
            assertThat(articles.clearCacheCalls).isEqualTo(1)
        }

    @Test
    fun aFailedClearIsReturnedNotThrown() =
        runTest {
            val full =
                object : ArticleRepository by FakeArticleRepository() {
                    override suspend fun clearCache(): Unit = error("disk full")
                }

            assertThat(ClearArticleCacheUseCase(full)()).isInstanceOf(Result.Failure::class.java)
        }
}
