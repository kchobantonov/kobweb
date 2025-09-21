package com.varabyte.kobweb.api.http

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * A serialization-aware convenience method layered on top of [Request.Body.text].
 *
 * An exception will be thrown if the body type cannot be deserialized (either due to body text that is not valid JSON
 * or valid JSON that cannot be converted into the requested type).
 *
 * See also the ApiFetcher extension methods provided by this library for examples of how to send requests with a
 * serialized body, e.g. `window.api.post<ExampleRequest, ExampleResponse>(body = ...)`.
 */
suspend inline fun <reified T> Request.Body.decode(bodyDeserializer: DeserializationStrategy<T> = serializer()): T? {
    return text().let { bodyText ->
        Json.decodeFromString(bodyDeserializer, bodyText)
    }
}

@Deprecated("Use `body?.decode(...)` instead", ReplaceWith("body?.decode(bodyDeserializer)"))
suspend inline fun <reified T> Request.readBody(bodyDeserializer: DeserializationStrategy<T> = serializer()): T? {
    return body?.decode(bodyDeserializer)
}

/**
 * A serialization-aware convenience factory method that extends [Response.Body].
 */
inline fun <reified T> Response.Body.Companion.encodeJson(body: T, bodySerializer: SerializationStrategy<T> = serializer()): Response.Body {
    return Response.Body.json(Json.encodeToString(bodySerializer, body))
}

@Deprecated("Use `body = Body.from(...)` instead", ReplaceWith("body = Body.from(body, bodySerializer)"))
inline fun <reified T> Response.setBody(body: T, bodySerializer: SerializationStrategy<T> = serializer()) {
    this.body = Response.Body.encodeJson(body, bodySerializer)
}
