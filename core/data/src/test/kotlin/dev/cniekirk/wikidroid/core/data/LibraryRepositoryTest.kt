package dev.cniekirk.wikidroid.core.data

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.database.CachedArticleEntity
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class LibraryRepositoryTest : DatabaseRepositoryTest() {
    private val clock = FakeClock()
    private val preferences = FakePreferencesDataSource()
    private lateinit var repository: RoomLibraryRepository

    @Before
    fun createRepository() {
        repository =
            RoomLibraryRepository(
                bookmarkDao = database.bookmarkDao(),
                historyDao = database.historyDao(),
                preferences = preferences,
                clock = clock,
            )
    }

    private fun summary(title: String) =
        ArticleSummary(title = title, thumbnailUrl = "https://minecraft.wiki/$title.png")

    private suspend fun bookmarkTitles() = repository.bookmarks.first().map { it.title }

    private suspend fun historyTitles() = repository.history.first().map { it.title }

    @Test
    fun `bookmarks are listed newest first with the saved summary`() =
        runTest {
            repository.addBookmark(summary("Diamond"))
            clock.advanceBy(1000)
            repository.addBookmark(summary("Creeper"))

            val entries = repository.bookmarks.first()

            assertThat(entries.map { it.title }).containsExactly("Creeper", "Diamond").inOrder()
            assertThat(entries.first().thumbnailUrl).isEqualTo("https://minecraft.wiki/Creeper.png")
            assertThat(entries.last().timestamp).isEqualTo(Instant.fromEpochMilliseconds(1_000_000))
        }

    @Test
    fun `isBookmarked follows adds and removals, ignoring underscores`() =
        runTest {
            assertThat(repository.isBookmarked("Diamond_Ore").first()).isFalse()

            repository.addBookmark(summary("Diamond Ore"))
            assertThat(repository.isBookmarked("Diamond_Ore").first()).isTrue()

            repository.removeBookmark("Diamond_Ore")
            assertThat(repository.isBookmarked("Diamond Ore").first()).isFalse()
        }

    @Test
    fun `a restored bookmark keeps its original time`() =
        runTest {
            repository.addBookmark(summary("Diamond"))
            clock.advanceBy(5000)
            repository.addBookmark(summary("Creeper"))
            val diamond = repository.bookmarks.first().last()

            repository.removeBookmark("Diamond")
            clock.advanceBy(5000)
            repository.restoreBookmark(diamond)

            assertThat(bookmarkTitles()).containsExactly("Creeper", "Diamond").inOrder()
            assertThat(
                repository.bookmarks
                    .first()
                    .last()
                    .timestamp,
            ).isEqualTo(diamond.timestamp)
        }

    @Test
    fun `entries report whether the article is cached for offline reading`() =
        runTest {
            repository.addBookmark(summary("Diamond"))
            repository.addBookmark(summary("Creeper"))
            database.cachedArticleDao().upsert(CachedArticleEntity("Diamond", 1, "<p/>", 0))

            val offline = repository.bookmarks.first().associate { it.title to it.isAvailableOffline }

            assertThat(offline).containsExactly("Diamond", true, "Creeper", false)
        }

    @Test
    fun `viewing a page again moves it to the top of the history`() =
        runTest {
            repository.recordVisit(summary("Diamond"))
            clock.advanceBy(1000)
            repository.recordVisit(summary("Creeper"))
            clock.advanceBy(1000)
            repository.recordVisit(summary("Diamond"))

            assertThat(historyTitles()).containsExactly("Diamond", "Creeper").inOrder()
        }

    @Test
    fun `nothing is recorded when saving history is switched off`() =
        runTest {
            preferences.updatePreferences { it.copy(saveHistory = false) }

            repository.recordVisit(summary("Diamond"))

            assertThat(historyTitles()).isEmpty()

            preferences.updatePreferences { it.copy(saveHistory = true) }
            repository.recordVisit(summary("Diamond"))
            assertThat(historyTitles()).containsExactly("Diamond")
        }

    @Test
    fun `history entries can be removed, restored and cleared`() =
        runTest {
            repository.recordVisit(summary("Diamond"))
            clock.advanceBy(1000)
            repository.recordVisit(summary("Creeper"))
            val creeper = repository.history.first().first()

            repository.removeFromHistory("Creeper")
            assertThat(historyTitles()).containsExactly("Diamond")

            repository.restoreHistoryEntry(creeper)
            assertThat(historyTitles()).containsExactly("Creeper", "Diamond").inOrder()

            repository.clearHistory()
            assertThat(historyTitles()).isEmpty()
        }

    @Test
    fun `clearing the history leaves bookmarks alone`() =
        runTest {
            repository.addBookmark(summary("Diamond"))
            repository.recordVisit(summary("Diamond"))

            repository.clearHistory()

            assertThat(bookmarkTitles()).containsExactly("Diamond")
        }
}
