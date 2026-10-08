package dev.cniekirk.wikidroid.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Every `api.php` response can carry an `error` object even with HTTP 200, so each response type
 * exposes it for [dev.cniekirk.wikidroid.core.network.mediaWikiCall] to map.
 */
interface MediaWikiResponse {
    val error: ApiErrorDto?
}

@Serializable
data class ApiErrorDto(
    val code: String,
    val info: String? = null,
)
