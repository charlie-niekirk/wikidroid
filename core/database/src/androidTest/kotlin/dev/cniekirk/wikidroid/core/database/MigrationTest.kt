package dev.cniekirk.wikidroid.core.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Each schema version must open from the one before it with the user's rows intact. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper =
        MigrationTestHelper(
            instrumentation = instrumentation,
            file = instrumentation.targetContext.getDatabasePath(TEST_DB),
            driver = BundledSQLiteDriver(),
            databaseClass = WikiDatabase::class,
        )

    @Test
    fun version1To2KeepsBookmarksAndAddsPointsOfInterest() =
        runBlocking {
            helper.createDatabase(1).use { connection ->
                connection.execSQL(
                    "INSERT INTO bookmarks (title, displayTitle, thumbnailUrl, savedAt) " +
                        "VALUES ('Diamond', 'Diamond', NULL, 1)",
                )
            }

            helper.runMigrationsAndValidate(2, emptyList()).use { connection ->
                assertThat(connection.single("SELECT title FROM bookmarks")).isEqualTo("Diamond")
                connection.execSQL(
                    "INSERT INTO points_of_interest " +
                        "(seed, dimension, x, z, y, name, category, color, note, createdAt) " +
                        "VALUES (262, 'OVERWORLD', 1, 2, NULL, 'Base', 'BASE', 'BLUE', NULL, 3)",
                )
                assertThat(connection.single("SELECT name FROM points_of_interest")).isEqualTo("Base")
            }
        }

    private fun SQLiteConnection.single(sql: String): String =
        prepare(sql).use { statement ->
            check(statement.step()) { "No row for $sql" }
            statement.getText(0)
        }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
