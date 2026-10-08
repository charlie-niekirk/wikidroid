package dev.cniekirk.wikidroid.core.model

import androidx.compose.runtime.Immutable

/** The newest released and pre-release version of each edition; `null` when unknown. */
@Immutable
data class LatestVersions(
    val java: String?,
    val javaSnapshot: String?,
    val bedrock: String?,
    val bedrockPreview: String?,
)
