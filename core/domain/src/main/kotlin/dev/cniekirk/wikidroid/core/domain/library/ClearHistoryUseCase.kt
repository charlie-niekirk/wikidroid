package dev.cniekirk.wikidroid.core.domain.library

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.dataResultOf
import dev.cniekirk.wikidroid.core.data.LibraryRepository
import dev.zacsweers.metro.Inject

/** Deletes every viewed page from the history. A failure is returned, not thrown. */
@Inject
class ClearHistoryUseCase(
    private val library: LibraryRepository,
) {
    suspend operator fun invoke(): Result<Unit, DataError> = dataResultOf { library.clearHistory() }
}
