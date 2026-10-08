package dev.cniekirk.wikidroid.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/** `action=query` result whose `pages` come from a generator (search, category members, random) or `titles`. */
@Serializable
data class PageQueryResponse(
    override val error: ApiErrorDto? = null,
    @SerialName("continue") val continuation: Map<String, JsonPrimitive>? = null,
    val query: PageQueryDto? = null,
) : MediaWikiResponse

@Serializable
data class PageQueryDto(
    val pages: List<PageDto> = emptyList(),
    val redirects: List<RedirectDto> = emptyList(),
)

@Serializable
data class RedirectDto(
    val from: String,
    val to: String,
)

@Serializable
data class PageDto(
    @SerialName("pageid") val pageId: Long? = null,
    val ns: Int = 0,
    val title: String,
    /** Rank within a search generator's results; absent for other generators. */
    val index: Int? = null,
    val missing: Boolean = false,
    val thumbnail: ThumbnailDto? = null,
    val extract: String? = null,
    @SerialName("lastrevid") val lastRevisionId: Long? = null,
    @SerialName("fullurl") val fullUrl: String? = null,
)

@Serializable
data class ThumbnailDto(
    val source: String,
    val width: Int? = null,
    val height: Int? = null,
)
