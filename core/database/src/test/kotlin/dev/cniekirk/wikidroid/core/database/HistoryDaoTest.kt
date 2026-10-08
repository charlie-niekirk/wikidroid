package dev.cniekirk.wikidroid.core.database

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HistoryDaoTest : DatabaseTest() {
    @Test
    fun `history is listed most recent first`() =
        runTest {
            history.upsert(visit("Creeper", viewedAt = 100))
            history.upsert(visit("Zombie", viewedAt = 300))
            history.upsert(visit("Diamond", viewedAt = 200))

            assertThat(history.observeAll().first().map { it.title })
                .containsExactly("Zombie", "Diamond", "Creeper")
                .inOrder()
        }

    @Test
    fun `revisiting a page moves it to the top without duplicating it`() =
        runTest {
            history.upsert(visit("Creeper", viewedAt = 100))
            history.upsert(visit("Zombie", viewedAt = 200))

            history.upsert(visit("Creeper", viewedAt = 300))

            val rows = history.observeAll().first()
            assertThat(rows.map { it.title }).containsExactly("Creeper", "Zombie").inOrder()
            assertThat(rows.first().timestamp).isEqualTo(300)
        }

    @Test
    fun `delete removes a single entry`() =
        runTest {
            history.upsert(visit("Creeper"))
            history.upsert(visit("Zombie"))

            history.delete("Creeper")

            assertThat(history.observeAll().first().map { it.title }).containsExactly("Zombie")
        }

    @Test
    fun `clear empties the history but not the bookmarks`() =
        runTest {
            history.upsert(visit("Creeper"))
            history.upsert(visit("Zombie"))
            bookmarks.upsert(bookmark("Creeper"))

            history.clear()

            assertThat(history.observeAll().first()).isEmpty()
            assertThat(bookmarks.observeAll().first()).hasSize(1)
        }

    @Test
    fun `offline availability comes from the article cache`() =
        runTest {
            history.upsert(visit("Creeper"))
            history.upsert(visit("Zombie"))
            articles.upsert(cached("Zombie"))

            val offline = history.observeAll().first().associate { it.title to it.isAvailableOffline }

            assertThat(offline).containsExactly("Creeper", false, "Zombie", true)
        }
}
