package dev.cniekirk.wikidroid.core.testing.fake

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.model.Article
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.model.UserPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakesTest {
    private fun article(
        title: String,
        revisionId: Long = 1,
    ) = Article(title = title, displayTitle = title, revisionId = revisionId, sections = persistentListOf())

    @Test
    fun articleRepository_emitsWhatWasPushedAndRecordsRequests() =
        runTest {
            val repository = FakeArticleRepository()
            repository.emit(article("Diamond Ore"))

            val first = repository.getArticle("Diamond_Ore").first()

            assertThat(first).isEqualTo(Result.Success(article("Diamond Ore")))
            assertThat(repository.requestedTitles).containsExactly("Diamond_Ore")
        }

    @Test
    fun articleRepository_emitsFailuresToo() =
        runTest {
            val repository = FakeArticleRepository()
            repository.emit("Diamond", Result.Failure(DataError.NotFound))

            assertThat(repository.getArticle("Diamond").first()).isEqualTo(Result.Failure(DataError.NotFound))
        }

    @Test
    fun articleRepository_countsCacheClears() =
        runTest {
            val repository = FakeArticleRepository()

            repository.clearCache()

            assertThat(repository.clearCacheCalls).isEqualTo(1)
        }

    @Test
    fun libraryRepository_keepsBookmarksNewestFirst() =
        runTest {
            val repository = FakeLibraryRepository()
            repository.addBookmark(ArticleSummary("Diamond"))
            repository.addBookmark(ArticleSummary("Creeper"))

            assertThat(repository.bookmarks.first().map { it.title }).containsExactly("Creeper", "Diamond").inOrder()
            assertThat(repository.isBookmarked("Creeper").first()).isTrue()

            repository.removeBookmark("Creeper")
            assertThat(repository.isBookmarked("Creeper").first()).isFalse()
        }

    @Test
    fun libraryRepository_restoresEntriesAtTheirOriginalPosition() =
        runTest {
            val repository = FakeLibraryRepository()
            repository.addBookmark(ArticleSummary("Diamond"))
            repository.addBookmark(ArticleSummary("Creeper"))
            val diamond = repository.bookmarks.first().last()

            repository.removeBookmark("Diamond")
            repository.addBookmark(ArticleSummary("Zombie"))
            repository.restoreBookmark(diamond)

            assertThat(
                repository.bookmarks.first().map { it.title },
            ).containsExactly("Zombie", "Creeper", "Diamond").inOrder()
        }

    @Test
    fun libraryRepository_historyHonoursSaveHistoryAndMarksOfflineTitles() =
        runTest {
            val repository = FakeLibraryRepository()
            repository.offlineTitles += "Diamond"
            repository.recordVisit(ArticleSummary("Diamond"))
            repository.saveHistory = false
            repository.recordVisit(ArticleSummary("Creeper"))

            val history = repository.history.first()

            assertThat(history.map { it.title }).containsExactly("Diamond")
            assertThat(history.single().isAvailableOffline).isTrue()

            repository.clearHistory()
            assertThat(repository.history.first()).isEmpty()
        }

    @Test
    fun searchRepository_recentSearchesDeduplicateAndCap() =
        runTest {
            val repository = FakeSearchRepository()
            (1..12).forEach { repository.saveRecentSearch("query $it") }
            repository.saveRecentSearch("QUERY 5")

            val recent = repository.recentSearches.first()

            assertThat(recent).hasSize(10)
            assertThat(recent.first()).isEqualTo("QUERY 5")
            assertThat(recent.count { it.equals("query 5", ignoreCase = true) }).isEqualTo(1)
        }

    @Test
    fun searchRepository_usesTheScriptedHandlers() =
        runTest {
            val repository = FakeSearchRepository()
            repository.autocompleteHandler = { Result.Success(listOf(ArticleSummary(it))) }

            val results = repository.autocomplete("dia")

            assertThat(results).isEqualTo(Result.Success(listOf(ArticleSummary("dia"))))
            assertThat(repository.autocompleteQueries).containsExactly("dia")
            assertThat(repository.search("dia", "next")).isInstanceOf(Result.Success::class.java)
            assertThat(repository.searchRequests).containsExactly("dia" to "next")
        }

    @Test
    fun settingsRepository_updatesAndClampsTheTextScale() =
        runTest {
            val repository = FakeSettingsRepository()

            repository.setThemeMode(ThemeMode.Dark)
            repository.setTextScale(9f)

            assertThat(repository.preferences.first().themeMode).isEqualTo(ThemeMode.Dark)
            assertThat(repository.current.textScale).isEqualTo(UserPreferences.MAX_TEXT_SCALE)
        }

    @Test
    fun wikiInfoRepository_countsCalls() =
        runTest {
            val repository = FakeWikiInfoRepository()

            repository.latestVersions()
            repository.randomArticle()
            repository.randomArticle()

            assertThat(repository.latestVersionsCalls).isEqualTo(1)
            assertThat(repository.randomArticleCalls).isEqualTo(2)
        }
}
