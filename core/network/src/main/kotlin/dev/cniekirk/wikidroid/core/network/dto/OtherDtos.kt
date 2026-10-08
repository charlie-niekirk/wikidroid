package dev.cniekirk.wikidroid.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExpandTemplatesResponse(
    override val error: ApiErrorDto? = null,
    @SerialName("expandtemplates") val expandTemplates: ExpandedTextDto? = null,
) : MediaWikiResponse

@Serializable
data class ExpandedTextDto(
    val wikitext: String,
)

/** `rest.php/v1/search/title`. Errors there are plain HTTP statuses, so there is no `error` field. */
@Serializable
data class RestSearchResponse(
    val pages: List<RestPageDto> = emptyList(),
)

@Serializable
data class RestPageDto(
    val id: Long,
    /** URL-style title, e.g. `Diamond_Ore`. */
    val key: String,
    val title: String,
    val excerpt: String? = null,
    val description: String? = null,
    val thumbnail: RestThumbnailDto? = null,
)

@Serializable
data class RestThumbnailDto(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)
