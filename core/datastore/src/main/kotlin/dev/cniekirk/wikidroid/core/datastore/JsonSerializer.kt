package dev.cniekirk.wikidroid.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Stores a `@Serializable` value as JSON. Unreadable content raises [CorruptionException], which the
 * DataStore's corruption handler turns back into the default value.
 */
class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
    private val json: Json = DataStoreJson,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T =
        try {
            json.decodeFromString(serializer, input.readBytes().decodeToString())
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot read ${serializer.descriptor.serialName} as JSON", exception)
        }

    override suspend fun writeTo(
        t: T,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }
}

/**
 * Lenient on read so a file written by another app version still loads: unknown keys are dropped and
 * an unrecognised enum value falls back to the field's default. Defaults are written out, which keeps
 * the file readable for debugging.
 */
val DataStoreJson: Json =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }
