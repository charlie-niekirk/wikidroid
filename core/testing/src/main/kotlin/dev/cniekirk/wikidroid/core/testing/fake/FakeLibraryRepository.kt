package dev.cniekirk.wikidroid.core.testing.fake

import dev.cniekirk.wikidroid.core.data.LibraryRepository
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.LibraryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

/**
 * In-memory bookmarks and history. Each write is stamped with a clock that advances by a second, so
 * ordering is deterministic. [saveHistory] mirrors the "save history" preference, and entries whose title
 * is in [offlineTitles] report `isAvailableOffline`.
 */
class FakeLibraryRepository : LibraryRepository {
    var saveHistory: Boolean = true
    val offlineTitles = mutableSetOf<String>()

    private var tick = 0L
    private val bookmarkEntries = MutableStateFlow<List<LibraryEntry>>(emptyList())
    private val historyEntries = MutableStateFlow<List<LibraryEntry>>(emptyList())

    override val bookmarks: Flow<List<LibraryEntry>> get() = bookmarkEntries.map { newestFirst(it) }
    override val history: Flow<List<LibraryEntry>> get() = historyEntries.map { newestFirst(it) }

    override fun isBookmarked(title: String): Flow<Boolean> =
        bookmarkEntries.map { entries -> entries.any { it.title == key(title) } }

    override suspend fun addBookmark(page: ArticleSummary) = bookmarkEntries.put(entryFor(page))

    override suspend fun removeBookmark(title: String) = bookmarkEntries.remove(title)

    override suspend fun restoreBookmark(entry: LibraryEntry) = bookmarkEntries.put(entry)

    override suspend fun recordVisit(page: ArticleSummary) {
        if (saveHistory) historyEntries.put(entryFor(page))
    }

    override suspend fun removeFromHistory(title: String) = historyEntries.remove(title)

    override suspend fun restoreHistoryEntry(entry: LibraryEntry) = historyEntries.put(entry)

    override suspend fun clearHistory() {
        historyEntries.value = emptyList()
    }

    private fun entryFor(page: ArticleSummary) =
        LibraryEntry(
            title = key(page.title),
            displayTitle = page.displayTitle,
            thumbnailUrl = page.thumbnailUrl,
            timestamp = Instant.fromEpochSeconds(++tick),
            isAvailableOffline = false,
        )

    private fun newestFirst(entries: List<LibraryEntry>) =
        entries
            .map { it.copy(isAvailableOffline = it.title in offlineTitles) }
            .sortedByDescending { it.timestamp }

    private fun MutableStateFlow<List<LibraryEntry>>.put(entry: LibraryEntry) =
        update { list -> list.filterNot { it.title == entry.title } + entry }

    private fun MutableStateFlow<List<LibraryEntry>>.remove(title: String) =
        update { list -> list.filterNot { it.title == key(title) } }

    private fun key(title: String) = title.trim().replace('_', ' ')
}
