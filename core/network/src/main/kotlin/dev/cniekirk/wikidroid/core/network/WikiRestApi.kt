package dev.cniekirk.wikidroid.core.network

import dev.cniekirk.wikidroid.core.network.dto.RestSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/** `rest.php/v1` endpoints. */
interface WikiRestApi {
    /** Title autocomplete, with thumbnails. */
    @GET("rest.php/v1/search/title")
    suspend fun searchTitles(
        @Query("q") query: String,
        @Query("limit") limit: Int,
    ): RestSearchResponse
}
