package dev.cniekirk.wikidroid.core.testing.fake

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.data.WikiInfoRepository
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.LatestVersions

class FakeWikiInfoRepository : WikiInfoRepository {
    var latestVersionsResult: Result<LatestVersions, DataError> =
        Result.Success(
            LatestVersions(java = "1.21.5", javaSnapshot = null, bedrock = "1.21.70", bedrockPreview = null),
        )
    var randomArticleResult: Result<ArticleSummary, DataError> = Result.Success(ArticleSummary(title = "Creeper"))

    var latestVersionsCalls = 0
        private set
    var randomArticleCalls = 0
        private set

    override suspend fun latestVersions(): Result<LatestVersions, DataError> {
        latestVersionsCalls++
        return latestVersionsResult
    }

    override suspend fun randomArticle(): Result<ArticleSummary, DataError> {
        randomArticleCalls++
        return randomArticleResult
    }
}
