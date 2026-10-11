package dev.cniekirk.wikidroid.core.domain.search

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.data.SearchRepository
import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Paged
import dev.zacsweers.metro.Inject

/** A submitted search: remembers [query] in the recent searches, then fetches the first page of results. */
@Inject
class RunSearchUseCase(
    private val search: SearchRepository,
) {
    suspend operator fun invoke(query: String): Result<Paged<ArticleSummary>, DataError> {
        search.saveRecentSearch(query)
        return search.search(query, continuation = null)
    }
}
