package dev.cniekirk.wikidroid.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ParseResponse(
    override val error: ApiErrorDto? = null,
    val parse: ParsedPageDto? = null,
) : MediaWikiResponse

@Serializable
data class ParsedPageDto(
    val title: String,
    @SerialName("pageid") val pageId: Long? = null,
    @SerialName("revid") val revisionId: Long,
    /** HTML, e.g. `<span class="mw-page-title-main">Diamond</span>`. */
    @SerialName("displaytitle") val displayTitle: String? = null,
    /** The article body as MediaWiki HTML (mobile format, sections wrapped in `mf-section-N`). */
    val text: String,
    val sections: List<SectionDto> = emptyList(),
    val categories: List<ParsedCategoryDto> = emptyList(),
    val redirects: List<RedirectDto> = emptyList(),
)

@Serializable
data class SectionDto(
    /** MediaWiki sends these as strings even with `formatversion=2`. */
    val level: String,
    val line: String,
    val anchor: String,
    val number: String? = null,
    val index: String? = null,
)

@Serializable
data class ParsedCategoryDto(
    val category: String,
    val hidden: Boolean = false,
)
