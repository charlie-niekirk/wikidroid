package dev.cniekirk.wikidroid.core.data

import android.app.Application
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.cniekirk.wikidroid.core.articleparser.ArticleParser
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.database.WikiDatabase
import dev.cniekirk.wikidroid.core.datastore.PreferencesDataSource
import dev.cniekirk.wikidroid.core.model.ArticleSection
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.LatestVersions
import dev.cniekirk.wikidroid.core.model.RichText
import dev.cniekirk.wikidroid.core.model.UserPreferences
import dev.cniekirk.wikidroid.core.network.PageList
import dev.cniekirk.wikidroid.core.network.PageRevision
import dev.cniekirk.wikidroid.core.network.WikiRemoteDataSource
import dev.cniekirk.wikidroid.core.network.dto.PageDto
import dev.cniekirk.wikidroid.core.network.dto.ParsedPageDto
import dev.cniekirk.wikidroid.core.network.dto.RestPageDto
import dev.cniekirk.wikidroid.core.testing.RobolectricTest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Before
import kotlin.time.Clock
import kotlin.time.Instant

/** A [WikiRemoteDataSource] whose answers each test scripts; every call is recorded. */
class FakeRemote : WikiRemoteDataSource {
    var autocompleteResult: Result<List<RestPageDto>, DataError> = Result.Success(emptyList())
    var searchResult: Result<PageList, DataError> = Result.Success(PageList(emptyList(), null))
    var categoryResult: Result<PageList, DataError> = Result.Success(PageList(emptyList(), null))
    var parseResults: MutableMap<String, Result<ParsedPageDto, DataError>> = mutableMapOf()
    var revisionResults: MutableMap<String, Result<PageRevision, DataError>> = mutableMapOf()
    var versionsResult: Result<LatestVersions, DataError> = Result.Failure(DataError.NotFound)
    var randomBatches: ArrayDeque<Result<List<PageDto>, DataError>> = ArrayDeque()

    val calls = mutableListOf<String>()

    override suspend fun autocomplete(
        query: String,
        limit: Int,
    ): Result<List<RestPageDto>, DataError> {
        calls += "autocomplete:$query"
        return autocompleteResult
    }

    override suspend fun searchPages(
        query: String,
        continuation: String?,
    ): Result<PageList, DataError> {
        calls += "search:$query:$continuation"
        return searchResult
    }

    override suspend fun categoryMembers(
        categoryTitle: String,
        continuation: String?,
    ): Result<PageList, DataError> {
        calls += "category:$categoryTitle:$continuation"
        return categoryResult
    }

    override suspend fun parseArticle(title: String): Result<ParsedPageDto, DataError> {
        calls += "parse:$title"
        return parseResults[title] ?: Result.Failure(DataError.NotFound)
    }

    override suspend fun latestRevision(title: String): Result<PageRevision, DataError> {
        calls += "revision:$title"
        return revisionResults[title] ?: Result.Failure(DataError.NotFound)
    }

    override suspend fun latestVersions(): Result<LatestVersions, DataError> {
        calls += "versions"
        return versionsResult
    }

    override suspend fun randomPages(count: Int): Result<List<PageDto>, DataError> {
        calls += "random:$count"
        return randomBatches.removeFirstOrNull() ?: Result.Success(emptyList())
    }
}

class FakePreferencesDataSource(
    initial: UserPreferences = UserPreferences(),
) : PreferencesDataSource {
    private val preferences = MutableStateFlow(initial)
    private val recent = MutableStateFlow<List<String>>(emptyList())

    override val userPreferences: Flow<UserPreferences> get() = preferences
    override val recentSearches: Flow<List<String>> get() = recent

    override suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        preferences.update(transform)
    }

    override suspend fun addRecentSearch(query: String) {
        recent.update { listOf(query) + it }
    }

    override suspend fun removeRecentSearch(query: String) {
        recent.update { list -> list - query }
    }

    override suspend fun clearRecentSearches() {
        recent.value = emptyList()
    }
}

class FakeClock(
    var now: Instant = Instant.fromEpochMilliseconds(1_000_000),
) : Clock {
    override fun now(): Instant = now

    fun advanceBy(millis: Long) {
        now = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + millis)
    }
}

/** Turns an HTML string into one paragraph, so tests can see which HTML an article was built from. */
object EchoParser : ArticleParser {
    const val UNPARSEABLE = "unparseable"

    override fun parse(html: String) =
        if (html == UNPARSEABLE) {
            error("simulated parser failure")
        } else {
            persistentListOf(
                ArticleSection(heading = null, blocks = persistentListOf(ContentBlock.Paragraph(RichText.of(html)))),
            )
        }
}

fun parsedPage(
    title: String,
    revisionId: Long = 1,
    text: String = "<p>$title</p>",
) = ParsedPageDto(title = title, revisionId = revisionId, text = text)

fun page(
    title: String,
    ns: Int = 0,
    extract: String? = null,
    thumbnail: String? = null,
) = PageDto(
    ns = ns,
    title = title,
    extract = extract,
    thumbnail =
        thumbnail?.let {
            dev.cniekirk.wikidroid.core.network.dto
                .ThumbnailDto(it)
        },
    fullUrl = "https://minecraft.wiki/w/${title.replace(' ', '_')}",
)

/** An in-memory [WikiDatabase], on Robolectric's SQLite because the bundled driver has no host-JVM natives. */
abstract class DatabaseRepositoryTest : RobolectricTest() {
    protected lateinit var database: WikiDatabase

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Application>(),
                    WikiDatabase::class.java,
                ).setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }
}
