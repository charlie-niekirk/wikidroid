package dev.cniekirk.wikidroid.core.domain.search

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Paged
import dev.cniekirk.wikidroid.core.testing.fake.FakeSearchRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RunSearchUseCaseTest {
    private val repository = FakeSearchRepository()

    @Test
    fun remembersTheSearchBeforeFetchingTheFirstPage() =
        runTest {
            val page = Paged(persistentListOf(ArticleSummary(title = "Creeper")), continuation = "next")
            var recentWhenSearched = emptyList<String>()
            repository.searchHandler = { _, _ ->
                recentWhenSearched = repository.recentSearches.first()
                Result.Success(page)
            }

            val result = RunSearchUseCase(repository)("creeper")

            assertThat(result).isEqualTo(Result.Success(page))
            assertThat(repository.searchRequests).containsExactly("creeper" to null)
            assertThat(recentWhenSearched).containsExactly("creeper")
        }

    @Test
    fun aFailedSearchIsStillRemembered() =
        runTest {
            repository.searchHandler = { _, _ -> Result.Failure(DataError.Network()) }

            val result = RunSearchUseCase(repository)("creeper")

            assertThat(result).isEqualTo(Result.Failure(DataError.Network()))
            assertThat(repository.recentSearches.first()).containsExactly("creeper")
        }
}
