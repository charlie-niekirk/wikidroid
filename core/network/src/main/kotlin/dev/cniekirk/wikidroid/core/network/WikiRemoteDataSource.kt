package dev.cniekirk.wikidroid.core.network

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.flatMap
import dev.cniekirk.wikidroid.core.common.map
import dev.cniekirk.wikidroid.core.model.LatestVersions
import dev.cniekirk.wikidroid.core.network.dto.PageDto
import dev.cniekirk.wikidroid.core.network.dto.ParsedPageDto
import dev.cniekirk.wikidroid.core.network.dto.RestPageDto
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** One page of a paged listing. [continuation] is opaque; pass it back to load the next page. */
data class PageList(
    val pages: List<PageDto>,
    val continuation: String?,
)

/** The revision the wiki currently serves for a title, after following redirects. */
data class PageRevision(
    val title: String,
    val revisionId: Long,
)

/**
 * Typed access to minecraft.wiki. Every call returns a [Result], never throws, and has already
 * mapped transport failures and MediaWiki `error` bodies to a [DataError].
 */
interface WikiRemoteDataSource {
    suspend fun autocomplete(
        query: String,
        limit: Int = DEFAULT_AUTOCOMPLETE_LIMIT,
    ): Result<List<RestPageDto>, DataError>

    /** Full-text search over the main namespace, best match first. */
    suspend fun searchPages(
        query: String,
        continuation: String? = null,
    ): Result<PageList, DataError>

    /** Pages and subcategories (namespaces 0 and 14). [categoryTitle] includes the `Category:` prefix. */
    suspend fun categoryMembers(
        categoryTitle: String,
        continuation: String? = null,
    ): Result<PageList, DataError>

    /** [DataError.NotFound] when the page doesn't exist. */
    suspend fun parseArticle(title: String): Result<ParsedPageDto, DataError>

    /** [DataError.NotFound] when the page doesn't exist. */
    suspend fun latestRevision(title: String): Result<PageRevision, DataError>

    suspend fun latestVersions(): Result<LatestVersions, DataError>

    /** Random non-redirect articles; the caller filters out the ones it doesn't want and may ask again. */
    suspend fun randomPages(count: Int = DEFAULT_RANDOM_COUNT): Result<List<PageDto>, DataError>

    companion object {
        const val DEFAULT_AUTOCOMPLETE_LIMIT = 10
        const val DEFAULT_RANDOM_COUNT = 5
    }
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RetrofitWikiRemoteDataSource(
    private val api: MediaWikiApi,
    private val restApi: WikiRestApi,
) : WikiRemoteDataSource {
    override suspend fun autocomplete(
        query: String,
        limit: Int,
    ): Result<List<RestPageDto>, DataError> = restCall { restApi.searchTitles(query, limit) }.map { it.pages }

    override suspend fun searchPages(
        query: String,
        continuation: String?,
    ): Result<PageList, DataError> =
        mediaWikiCall { api.searchPages(query, Continuation.decode(continuation)) }
            .map { response ->
                PageList(
                    pages =
                        response.query
                            ?.pages
                            .orEmpty()
                            .sortedBy { it.index ?: Int.MAX_VALUE },
                    continuation = Continuation.encode(response.continuation),
                )
            }

    override suspend fun categoryMembers(
        categoryTitle: String,
        continuation: String?,
    ): Result<PageList, DataError> =
        mediaWikiCall { api.categoryMembers(categoryTitle, Continuation.decode(continuation)) }
            .map { response ->
                PageList(
                    pages = response.query?.pages.orEmpty(),
                    continuation = Continuation.encode(response.continuation),
                )
            }

    override suspend fun parseArticle(title: String): Result<ParsedPageDto, DataError> =
        mediaWikiCall { api.parseArticle(title) }.flatMap { response ->
            response.parse?.let { Result.Success(it) } ?: Result.Failure(DataError.Parse)
        }

    override suspend fun latestRevision(title: String): Result<PageRevision, DataError> =
        mediaWikiCall { api.revisionInfo(title) }.flatMap { response ->
            val page = response.query?.pages?.firstOrNull()
            val revisionId = page?.lastRevisionId
            when {
                page == null -> Result.Failure(DataError.Parse)
                page.missing || revisionId == null -> Result.Failure(DataError.NotFound)
                else -> Result.Success(PageRevision(page.title, revisionId))
            }
        }

    override suspend fun latestVersions(): Result<LatestVersions, DataError> =
        mediaWikiCall { api.expandTemplates(LATEST_VERSIONS_TEMPLATE) }.flatMap { response ->
            response.expandTemplates
                ?.let { Result.Success(parseLatestVersions(it.wikitext)) }
                ?: Result.Failure(DataError.Parse)
        }

    override suspend fun randomPages(count: Int): Result<List<PageDto>, DataError> =
        mediaWikiCall { api.randomPages(count) }.map { it.query?.pages.orEmpty() }
}
