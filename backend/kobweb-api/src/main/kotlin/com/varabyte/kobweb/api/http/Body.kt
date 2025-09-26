package com.varabyte.kobweb.api.http

import com.varabyte.kobweb.io.ByteSource
import com.varabyte.kobweb.io.RawByteSource
import com.varabyte.kobweb.io.toByteSource
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Interface to tag body companion objects with, extending them with helpful factory methods to create bodies with.
 *
 * If a user wants to add their own custom body type, they should extend this class
 * (`fun <B> BodyFactory<B>.create(...)`), internally delegating to [invoke] or [bytes] (the latter may be more
 * convenient for when you're sure you can fit the whole content in memory).
 *
 * If you extend this interface, you should *also* create an associated method on top of [ContentSource].
 */
interface BodyFactory<B> {
    fun invoke(contentType: String = "application/octet-stream", provideContent: suspend () -> ByteSource): B
}

fun <B> BodyFactory<B>.stream(inputStream: InputStream, contentType: String = "application/octet-stream") =
    invoke(contentType) { inputStream.toByteSource() }

fun <B> BodyFactory<B>.bytes(bytes: ByteArray, contentType: String = "application/octet-stream") =
    invoke(contentType) { RawByteSource(bytes) }

fun <B> BodyFactory<B>.text(
    text: String,
    charset: Charset = Charsets.UTF_8,
    contentType: String = "text/plain; charset=${charset.name()}"
) = bytes(text.toByteArray(charset), contentType)

fun <B> BodyFactory<B>.json(text: String, contentType: String = "application/json") =
    text(text, contentType = contentType)
