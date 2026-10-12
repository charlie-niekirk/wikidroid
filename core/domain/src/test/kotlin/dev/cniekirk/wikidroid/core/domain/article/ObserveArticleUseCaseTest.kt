package dev.cniekirk.wikidroid.core.domain.article

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.data.ArticleRepository
import dev.cniekirk.wikidroid.core.data.LibraryRepository
import dev.cniekirk.wikidroid.core.model.Article
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.testing.fake.FakeLibraryRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveArticleUseCaseTest {
    private val library = FakeLibraryRepository()

    private fun article(revision: Long) =
        Article(title = "Diamond", displayTitle = "Diamond", revisionId = revision, sections = persistentListOf())

    private fun repository(vararg results: Result<Article, DataError>) =
        object : ArticleRepository {
            override fun getArticle(title: String): Flow<Result<Article, DataError>> = flowOf(*results)

            override suspend fun clearCache() = Unit
        }

    @Test
    fun passesEveryCopyThroughAndRecordsOneVisit() =
        runTest {
            val cached = Result.Success(article(revision = 1))
            val refreshed = Result.Success(article(revision = 2))

            val emitted = ObserveArticleUseCase(repository(cached, refreshed), library)("Diamond").toList()

            assertThat(emitted).containsExactly(cached, refreshed).inOrder()
            assertThat(library.history.first().map { it.title }).containsExactly("Diamond")
        }

    @Test
    fun theVisitIsRecordedAfterThePageIsHandedOver() =
        runTest {
            val historyWhenShown = mutableListOf<Int>()

            ObserveArticleUseCase(repository(Result.Success(article(revision = 1))), library)("Diamond")
                .collect { historyWhenShown += library.history.first().size }

            assertThat(historyWhenShown).containsExactly(0)
            assertThat(library.history.first()).hasSize(1)
        }

    @Test
    fun aFailureRecordsNoVisit() =
        runTest {
            val emitted =
                ObserveArticleUseCase(
                    repository(Result.Failure(DataError.NotFound)),
                    library,
                )("Diamond").toList()

            assertThat(emitted).containsExactly(Result.Failure(DataError.NotFound))
            assertThat(library.history.first()).isEmpty()
        }

    @Test
    fun aFailedHistoryWriteDoesNotFailThePage() =
        runTest {
            val broken =
                object : LibraryRepository by FakeLibraryRepository() {
                    override suspend fun recordVisit(page: ArticleSummary): Unit = error("database locked")
                }
            val refreshed = Result.Success(article(revision = 2))

            val emitted =
                ObserveArticleUseCase(repository(Result.Success(article(revision = 1)), refreshed), broken)("Diamond")
                    .toList()

            assertThat(emitted.last()).isEqualTo(refreshed)
        }
}
