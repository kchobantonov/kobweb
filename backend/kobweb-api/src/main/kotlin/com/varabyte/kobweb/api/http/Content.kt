package com.varabyte.kobweb.api.http

import com.varabyte.kobweb.api.http.io.parseCharsetFromContentType
import com.varabyte.kobweb.framework.annotations.DelicateApi
import com.varabyte.kobweb.io.ByteSource
import com.varabyte.kobweb.io.readRemaining
import com.varabyte.kobweb.io.toInputStream
import java.io.InputStream
import java.nio.charset.Charset

interface ContentSource {
    val contentType: String?
    val contentLength: Long?

    @DelicateApi("Kobweb created a custom I/O class because kotlinx-io doesn't have an async byte stream concept, but we may migrate over at some point in the future if this ever changes. Consider using higher level helper methods instead, like `bytes()` or `text()`.")
    suspend fun openContent(): ByteSource
}

class ContentDisposition(val disposition: String, val parameters: Map<String, String> = mapOf()) {
    val name: String? get() = parameters[Parameters.Name]

    /**
     * Frequently used content disposition parameter names
     */
    // See also: https://github.com/ktorio/ktor/blob/af24a5a1a663d6c3c4fe36360a565fe17461b2d5/ktor-http/common/src/io/ktor/http/ContentDisposition.kt#L105
    @Suppress("KDocMissingDocumentation", "unused", "PublicApiImplicitType", "ConstPropertyName")
    object Parameters {
        const val FileName: String = "filename"
        const val FileNameAsterisk: String = "filename*"
        const val Name: String = "name"
        const val CreationDate: String = "creation-date"
        const val ModificationDate: String = "modification-date"
        const val ReadDate: String = "read-date"
        const val Size: String = "size"
        const val Handling: String = "handling"
    }
}

/**
 * Convenience method to convert a body's content into a raw byte array
 *
 * @param limit If set and the size of the body is larger than it, throw an exception.
 */
suspend fun ContentSource.bytes(limit: Int? = null): ByteArray {
    @OptIn(DelicateApi::class)
    return openContent().use { it.readRemaining(limit) }
}

/**
 * Convenience method to convert a body's content into a UTF-8 string.
 *
 * @param limit If set and the size of the body is larger than it, throw an exception.
 */
suspend fun ContentSource.text(charset: Charset = contentType.parseCharsetFromContentType(), limit: Int? = null): String {
    return bytes(limit).toString(charset)
}
