package dev.cniekirk.wikidroid.core.model

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

/** A bookmark or a history item. [timestamp] is when it was saved or last viewed. */
@Immutable
data class LibraryEntry(
    val title: String,
    val displayTitle: String,
    val thumbnailUrl: String?,
    val timestamp: Instant,
    val isAvailableOffline: Boolean,
)
