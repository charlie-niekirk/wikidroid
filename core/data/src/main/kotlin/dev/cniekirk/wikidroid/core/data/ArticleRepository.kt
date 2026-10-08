package dev.cniekirk.wikidroid.core.data

import dev.cniekirk.wikidroid.core.articleparser.ArticleParser
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.DefaultDispatcher
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.dataResultOf
import dev.cniekirk.wikidroid.core.common.flatMap
import dev.cniekirk.wikidroid.core.common.getOrNull
import dev.cniekirk.wikidroid.core.common.map
import dev.cniekirk.wikidroid.core.database.CachedArticleDao
import dev.cniekirk.wikidroid.core.database.CachedArticleEntity
import dev.cniekirk.wikidroid.core.model.Article
import dev.cniekirk.wikidroid.core.model.ArticleSection
import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.network.WikiBaseUrl
import dev.cniekirk.wikidroid.core.network.WikiRemoteDataSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import kotlin.time.Clock

interface ArticleRepository {
    /**
     * Loads an article offline-first.
     *
     * 1. If a copy is cached it is emitted straight away.
     * 2. The wiki is then asked for the page's current revision. When it differs from the cached one the
     *    page is downloaded, the cache is updated and the new version is emitted. When it is the same
     *    nothing more is emitted.
     * 3. With no cached copy the page is downloaded and emitted, or a failure is emitted.
     *
     * Once a cached copy has been emitted, network failures are swallowed: the reader keeps what they have.
     * [title] may be a redirect or use underscores; the emitted [Article.title] is the canonical one.
     */
    fun getArticle(title: String): Flow<Result<Article, DataError>>

    /** Deletes every cached article except the bookmarked ones, which stay readable offline. */
    suspend fun clearCache()
}

/** Unbookmarked articles beyond this many (most recently fetched kept) are deleted after each download. */
internal const val MAX_CACHED_ARTICLES = 100

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class OfflineFirstArticleRepository(
    private val remote: WikiRemoteDataSource,
    private val cache: CachedArticleDao,
    private val parser: ArticleParser,
    private val clock: Clock,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @WikiBaseUrl private val baseUrl: HttpUrl,
) : ArticleRepository {
    override fun getArticle(title: String): Flow<Result<Article, DataError>> =
        flow {
            val requested = normalizeTitle(title)
            val cached = cache.get(requested)
            val cachedArticle = cached?.let { toArticle(it.title, it.revisionId, it.html).getOrNull() }
            if (cached == null || cachedArticle == null) {
                // Missing or unreadable (e.g. written by an incompatible parser): download it.
                emit(download(requested))
            } else {
                emit(Result.Success(cachedArticle))
                refreshIfStale(requested, cached)
            }
        }

    override suspend fun clearCache() = cache.pruneUnbookmarked(keep = 0)

    private suspend fun FlowCollector<Result<Article, DataError>>.refreshIfStale(
        requested: String,
        cached: CachedArticleEntity,
    ) {
        val latest = remote.latestRevision(requested).getOrNull() ?: return
        if (latest.revisionId == cached.revisionId && latest.title == cached.title) return
        val fresh = download(requested)
        // A failed refresh must not replace the copy the reader is already looking at.
        if (fresh is Result.Success) emit(fresh)
    }

    private suspend fun download(title: String): Result<Article, DataError> =
        remote.parseArticle(title).flatMap { parsed ->
            toArticle(parsed.title, parsed.revisionId, parsed.text).map { article ->
                val fetchedAt = clock.now().toEpochMilliseconds()
                store(CachedArticleEntity(parsed.title, parsed.revisionId, parsed.text, fetchedAt))
                article
            }
        }

    /** A full disk shouldn't stop an article that was downloaded fine from being shown. */
    private suspend fun store(entity: CachedArticleEntity) {
        dataResultOf {
            cache.upsert(entity)
            cache.pruneUnbookmarked(MAX_CACHED_ARTICLES)
        }
    }

    // Parsing is CPU-bound (tens of milliseconds for a big page), so it stays off the caller's dispatcher.
    private suspend fun toArticle(
        title: String,
        revisionId: Long,
        html: String,
    ): Result<Article, DataError> =
        dataResultOf(mapError = { DataError.Parse }) {
            val sections = withContext(defaultDispatcher) { parser.parse(html) }
            Article(
                title = title,
                displayTitle = title,
                revisionId = revisionId,
                sections = sections,
                pageUrl = pageUrl(title),
                thumbnailUrl = sections.thumbnailUrl(),
            )
        }

    private fun pageUrl(title: String): String =
        baseUrl
            .newBuilder()
            .addPathSegments("w/" + title.replace(' ', '_'))
            .build()
            .toString()
}

private fun List<ArticleSection>.thumbnailUrl(): String? {
    val lead = firstOrNull { it.heading == null }?.blocks.orEmpty()
    val infoboxImage = lead.filterIsInstance<ContentBlock.Infobox>().firstNotNullOfOrNull { it.images.firstOrNull() }
    val firstImage = lead.filterIsInstance<ContentBlock.Image>().firstOrNull()
    return (infoboxImage ?: firstImage)?.url
}
