package dev.cniekirk.wikidroid.core.data

import dev.cniekirk.wikidroid.core.database.BookmarkDao
import dev.cniekirk.wikidroid.core.database.BookmarkEntity
import dev.cniekirk.wikidroid.core.database.HistoryDao
import dev.cniekirk.wikidroid.core.database.HistoryEntity
import dev.cniekirk.wikidroid.core.database.LibraryRow
import dev.cniekirk.wikidroid.core.datastore.PreferencesDataSource
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.LibraryEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

/** Bookmarks and reading history. Entries are keyed by canonical page title. */
interface LibraryRepository {
    /** Newest first. Re-emits when a page's offline copy appears or is evicted. */
    val bookmarks: Flow<List<LibraryEntry>>

    /** Most recently viewed first. */
    val history: Flow<List<LibraryEntry>>

    fun isBookmarked(title: String): Flow<Boolean>

    suspend fun addBookmark(page: ArticleSummary)

    suspend fun removeBookmark(title: String)

    /** Puts back an entry removed by swipe-to-delete, keeping its original [LibraryEntry.timestamp]. */
    suspend fun restoreBookmark(entry: LibraryEntry)

    /** Moves [page] to the top of the history. Does nothing when the "save history" preference is off. */
    suspend fun recordVisit(page: ArticleSummary)

    suspend fun removeFromHistory(title: String)

    suspend fun restoreHistoryEntry(entry: LibraryEntry)

    suspend fun clearHistory()
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class RoomLibraryRepository(
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
    private val preferences: PreferencesDataSource,
    private val clock: Clock,
) : LibraryRepository {
    override val bookmarks: Flow<List<LibraryEntry>> =
        bookmarkDao.observeAll().map { rows -> rows.map(LibraryRow::toEntry) }

    override val history: Flow<List<LibraryEntry>> =
        historyDao.observeAll().map { rows -> rows.map(LibraryRow::toEntry) }

    override fun isBookmarked(title: String): Flow<Boolean> = bookmarkDao.observeIsBookmarked(normalizeTitle(title))

    override suspend fun addBookmark(page: ArticleSummary) {
        bookmarkDao.upsert(
            BookmarkEntity(
                title = normalizeTitle(page.title),
                displayTitle = page.displayTitle,
                thumbnailUrl = page.thumbnailUrl,
                savedAt = clock.now().toEpochMilliseconds(),
            ),
        )
    }

    override suspend fun removeBookmark(title: String) = bookmarkDao.delete(normalizeTitle(title))

    override suspend fun restoreBookmark(entry: LibraryEntry) {
        bookmarkDao.upsert(
            BookmarkEntity(
                title = entry.title,
                displayTitle = entry.displayTitle,
                thumbnailUrl = entry.thumbnailUrl,
                savedAt = entry.timestamp.toEpochMilliseconds(),
            ),
        )
    }

    override suspend fun recordVisit(page: ArticleSummary) {
        if (!preferences.userPreferences.first().saveHistory) return
        historyDao.upsert(
            HistoryEntity(
                title = normalizeTitle(page.title),
                displayTitle = page.displayTitle,
                thumbnailUrl = page.thumbnailUrl,
                viewedAt = clock.now().toEpochMilliseconds(),
            ),
        )
    }

    override suspend fun removeFromHistory(title: String) = historyDao.delete(normalizeTitle(title))

    override suspend fun restoreHistoryEntry(entry: LibraryEntry) {
        historyDao.upsert(
            HistoryEntity(
                title = entry.title,
                displayTitle = entry.displayTitle,
                thumbnailUrl = entry.thumbnailUrl,
                viewedAt = entry.timestamp.toEpochMilliseconds(),
            ),
        )
    }

    override suspend fun clearHistory() = historyDao.clear()
}

private fun LibraryRow.toEntry(): LibraryEntry =
    LibraryEntry(
        title = title,
        displayTitle = displayTitle,
        thumbnailUrl = thumbnailUrl,
        timestamp = Instant.fromEpochMilliseconds(timestamp),
        isAvailableOffline = isAvailableOffline,
    )
