package dev.cniekirk.wikidroid.core.domain.library

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.data.LibraryRepository
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.testing.fake.FakeLibraryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ClearHistoryUseCaseTest {
    @Test
    fun clearsTheHistory() =
        runTest {
            val library = FakeLibraryRepository()
            library.recordVisit(ArticleSummary(title = "Creeper"))

            val outcome = ClearHistoryUseCase(library)()

            assertThat(outcome).isEqualTo(Result.Success(Unit))
            assertThat(library.history.first()).isEmpty()
        }

    @Test
    fun aFailedClearIsReturnedNotThrown() =
        runTest {
            val locked =
                object : LibraryRepository by FakeLibraryRepository() {
                    override suspend fun clearHistory(): Unit = error("database locked")
                }

            assertThat(ClearHistoryUseCase(locked)()).isInstanceOf(Result.Failure::class.java)
        }
}
