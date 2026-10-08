package dev.cniekirk.wikidroid.core.network

import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.dataResultOf
import dev.cniekirk.wikidroid.core.common.flatMap
import dev.cniekirk.wikidroid.core.network.dto.ApiErrorDto
import dev.cniekirk.wikidroid.core.network.dto.MediaWikiResponse
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** `error.code` values that mean the requested page isn't there. */
private val NOT_FOUND_CODES = setOf("missingtitle", "invalidtitle")

/**
 * Runs an `api.php` call. Transport and decoding failures become [DataError.Network] or
 * [DataError.Parse], and MediaWiki's `{"error": ...}` body (sent with HTTP 200) becomes
 * [DataError.NotFound] or [DataError.Api].
 */
internal suspend fun <T : MediaWikiResponse> mediaWikiCall(block: suspend () -> T): Result<T, DataError> =
    dataResultOf(Throwable::toNetworkDataError) { block() }.flatMap { response ->
        val error = response.error
        if (error == null) Result.Success(response) else Result.Failure(error.toDataError())
    }

/** Runs a call to an endpoint that has no `error` envelope, such as `rest.php`. */
internal suspend fun <T> restCall(block: suspend () -> T): Result<T, DataError> =
    dataResultOf(Throwable::toNetworkDataError) { block() }

internal fun ApiErrorDto.toDataError(): DataError =
    if (code in NOT_FOUND_CODES) DataError.NotFound else DataError.Api(code, info)

internal fun Throwable.toNetworkDataError(): DataError =
    when (this) {
        is HttpException -> if (code() == HTTP_NOT_FOUND) DataError.NotFound else DataError.Network(code())
        is SerializationException -> DataError.Parse
        is IOException -> DataError.Network()
        else -> DataError.Unknown(this)
    }

private const val HTTP_NOT_FOUND = 404
