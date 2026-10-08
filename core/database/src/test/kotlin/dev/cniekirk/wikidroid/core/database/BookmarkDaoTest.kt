package dev.cniekirk.wikidroid.core.database

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BookmarkDaoTest : DatabaseTest() {
    @Test
    fun `bookmarks are listed newest first`() =
        runTest {
            bookmarks.upsert(bookmark("Creeper", savedAt = 100))
            bookmarks.upsert(bookmark("Zombie", savedAt = 300))
            bookmarks.upsert(bookmark("Diamond", savedAt = 200))

            val titles = bookmarks.observeAll().first().map { it.title }

            assertThat(titles).containsExactly("Zombie", "Diamond", "Creeper").inOrder()
        }

    @Test
    fun `a row carries the saved time as its timestamp`() =
        runTest {
            bookmarks.upsert(
                BookmarkEntity("Diamond", "Diamond", thumbnailUrl = "https://x/d.png", savedAt = 1234),
            )

            val row = bookmarks.observeAll().first().single()

            assertThat(row).isEqualTo(LibraryRow("Diamond", "Diamond", "https://x/d.png", 1234, false))
        }

    @Test
    fun `saving the same title twice keeps one row with the latest data`() =
        runTest {
            bookmarks.upsert(bookmark("Diamond", savedAt = 1))
            bookmarks.upsert(bookmark("Diamond", savedAt = 2))

            val rows = bookmarks.observeAll().first()

            assertThat(rows).hasSize(1)
            assertThat(rows.single().timestamp).isEqualTo(2)
        }

    @Test
    fun `observeIsBookmarked follows add and remove`() =
        runTest {
            bookmarks.observeIsBookmarked("Diamond").test {
                assertThat(awaitItem()).isFalse()
                bookmarks.upsert(bookmark("Diamond"))
                assertThat(awaitItem()).isTrue()
                bookmarks.delete("Diamond")
                assertThat(awaitItem()).isFalse()
            }
        }

    @Test
    fun `delete removes only that bookmark`() =
        runTest {
            bookmarks.upsert(bookmark("Diamond"))
            bookmarks.upsert(bookmark("Creeper"))

            bookmarks.delete("Diamond")

            assertThat(bookmarks.observeAll().first().map { it.title }).containsExactly("Creeper")
        }

    @Test
    fun `offline availability reflects the article cache and updates live`() =
        runTest {
            bookmarks.upsert(bookmark("Diamond"))

            bookmarks.observeAll().test {
                assertThat(awaitItem().single().isAvailableOffline).isFalse()
                articles.upsert(cached("Diamond"))
                assertThat(awaitItem().single().isAvailableOffline).isTrue()
                articles.delete("Diamond")
                assertThat(awaitItem().single().isAvailableOffline).isFalse()
            }
        }
}
