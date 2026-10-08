package dev.cniekirk.wikidroid.core.network

import dev.cniekirk.wikidroid.core.network.dto.ExpandTemplatesResponse
import dev.cniekirk.wikidroid.core.network.dto.PageQueryResponse
import dev.cniekirk.wikidroid.core.network.dto.ParseResponse
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

// `|` is written as %7C so the annotation values stay valid URLs. `format=json&formatversion=2` is
// added by DefaultParamsInterceptor. `exlimit` is capped at 20 by the server, which is why the paged
// listings below request 20 pages: asking for more makes MediaWiki continue the extracts on their own.
private const val PAGE_LIST_PROPS =
    "prop=pageimages%7Cextracts%7Cinfo&piprop=thumbnail&pithumbsize=160" +
        "&exintro=1&explaintext=1&exsentences=2&exlimit=max&inprop=url"

internal const val PAGE_SIZE = 20

/** `api.php` endpoints. Every call may return HTTP 200 with an `error` body; see [mediaWikiCall]. */
interface MediaWikiApi {
    @GET("api.php?action=query&generator=search&gsrnamespace=0&gsrlimit=$PAGE_SIZE&$PAGE_LIST_PROPS")
    suspend fun searchPages(
        @Query("gsrsearch") query: String,
        @QueryMap continuation: Map<String, String>,
    ): PageQueryResponse

    @GET("api.php?action=query&generator=categorymembers&gcmnamespace=0%7C14&gcmlimit=$PAGE_SIZE&$PAGE_LIST_PROPS")
    suspend fun categoryMembers(
        @Query("gcmtitle") categoryTitle: String,
        @QueryMap continuation: Map<String, String>,
    ): PageQueryResponse

    /** `maxage`/`smaxage` make the server answer with `Cache-Control: max-age=600`, so OkHttp caches it. */
    @GET(
        "api.php?action=parse&prop=text%7Csections%7Cdisplaytitle%7Ccategories%7Crevid" +
            "&mobileformat=1&disableeditsection=1&disabletoc=1&redirects=1&maxage=600&smaxage=600",
    )
    suspend fun parseArticle(
        @Query("page") title: String,
    ): ParseResponse

    /** Cheap freshness check: `lastrevid` of the (redirect-resolved) page. */
    @GET("api.php?action=query&prop=info&redirects=1")
    suspend fun revisionInfo(
        @Query("titles") title: String,
    ): PageQueryResponse

    @GET("api.php?action=expandtemplates&prop=wikitext")
    suspend fun expandTemplates(
        @Query("text") wikitext: String,
    ): ExpandTemplatesResponse

    @GET("api.php?action=query&generator=random&grnnamespace=0&grnfilterredir=nonredirects&$PAGE_LIST_PROPS")
    suspend fun randomPages(
        @Query("grnlimit") limit: Int,
    ): PageQueryResponse
}
