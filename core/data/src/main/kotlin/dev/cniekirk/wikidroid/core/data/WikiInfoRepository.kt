package dev.cniekirk.wikidroid.core.data

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.flatMap
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.LatestVersions
import dev.cniekirk.wikidroid.core.network.WikiRemoteDataSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

interface WikiInfoRepository {
    suspend fun latestVersions(): Result<LatestVersions, DataError>

    /** A random article that isn't a version, snapshot or version-history page. */
    suspend fun randomArticle(): Result<ArticleSummary, DataError>
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class WikiInfoRepositoryImpl(
    private val remote: WikiRemoteDataSource,
) : WikiInfoRepository {
    override suspend fun latestVersions(): Result<LatestVersions, DataError> = remote.latestVersions()

    override suspend fun randomArticle(): Result<ArticleSummary, DataError> {
        repeat(MAX_RANDOM_ATTEMPTS) {
            val batch = remote.randomPages()
            val pick =
                batch.flatMap { pages ->
                    pages
                        .firstOrNull { !it.title.looksLikeVersionPage() }
                        ?.let { Result.Success(it.toSummary()) }
                        ?: Result.Failure(DataError.NotFound)
                }
            // Only an empty or all-version batch is worth another request; a transport error is not.
            if (pick is Result.Success || batch is Result.Failure) return pick
        }
        return Result.Failure(DataError.NotFound)
    }

    private companion object {
        const val MAX_RANDOM_ATTEMPTS = 3
    }
}

private val versionPagePatterns =
    listOf(
        // "Java Edition 1.21", "Bedrock Edition 1.21.0", "Java Edition Beta 1.8", "Pocket Edition Alpha 0.1.0".
        Regex("""^(Java|Bedrock|Pocket|Legacy Console|Education|China|New Nintendo 3DS) Edition .*\d"""),
        // Snapshots: "24w14a".
        Regex("""^\d{2}w\d{2}[a-z]"""),
        Regex("""\bversion history\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(pre-release|release candidate)\b""", RegexOption.IGNORE_CASE),
    )

internal fun String.looksLikeVersionPage(): Boolean = versionPagePatterns.any { it.containsMatchIn(this) }
