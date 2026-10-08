package dev.cniekirk.wikidroid.core.network

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * MediaWiki continues a listing with a set of parameters (`gsroffset` plus `continue`, or
 * `gcmcontinue`, ...) that must be sent back unchanged. The set is packed into one opaque string
 * so it can live in `Paged.continuation`.
 */
internal object Continuation {
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    fun encode(parameters: Map<String, JsonPrimitive>?): String? =
        parameters
            ?.takeIf { it.isNotEmpty() }
            ?.let { Json.encodeToString(serializer, it.mapValues { (_, value) -> value.content }) }

    fun decode(token: String?): Map<String, String> = token?.let { Json.decodeFromString(serializer, it) }.orEmpty()
}
