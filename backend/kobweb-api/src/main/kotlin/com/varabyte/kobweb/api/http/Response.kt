package com.varabyte.kobweb.api.http

import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.data.MutableData
import com.varabyte.kobweb.api.intercept.ApiInterceptor
import com.varabyte.kobweb.framework.annotations.DelicateApi
import com.varabyte.kobweb.io.ByteSource
import com.varabyte.kobweb.io.RawByteSource
import com.varabyte.kobweb.io.toByteSource
import java.io.InputStream
import java.nio.charset.Charset

private val VALID_REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
private const val API_PREFIX = "/api"
private const val API_PREFIX_WITH_TRAILING_SLASH = "$API_PREFIX/"

/**
 * Data to send back to the client after it makes a request to an API endpoint.
 *
 * An empty successful response is automatically created and passed into an API via an [ApiContext]. Developers
 * implementing an API endpoint should modify this response with code like the following:
 *
 * ```
 * @Api
 * fun demo(ctx: ApiContext) {
 *   ctx.res.body = Response.Body.text("This is how you send text back to the client")
 * }
 * ```
 *
 * @see Request
 */
class Response {
    /**
     * The body of the response.
     *
     * Note that its contents can only be consumed once, via the [ByteSource] returned by [openContent].
     *
     * You can construct a body directly if you are comfortable working with [ByteSource], but several helper factory
     * methods are provided, such as [bytes] and [text].
     */
    class Body(
        /**
         * The content type of this body, e.g. "image/jpeg" or "application/json". Can include parameters.
         *
         * @see <a href="https://www.w3.org/Protocols/rfc1341/4_Content-Type.html">The Content-Type Header Field</a>
         */
        val contentType: String = "application/octet-stream",
        private val provideContent: suspend () -> ByteSource,
    ) {
        @Suppress("RemoveEmptyClassBody") // Necessary to avoid confusion with constructor below
        companion object {} // Declared so we can extend it with factory methods

        @DelicateApi("Kobweb created a custom I/O class because kotlinx-io doesn't have an async byte stream concept, but we may migrate over at some point in the future if this ever changes. Consider using higher level factory methods instead, like `Response.Body.bytes()` or `Response.Body.text()`.")
        suspend fun openContent() = provideContent()
    }

    private var _status: Int? = null

    /** @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Status">HTTP response status codes</a> */
    var status: Int
        get() = _status ?: 400
        set(value) {
            _status = value
        }

    /**
     * The body payload.
     *
     * Leave null if there should be no body to send back with this response.
     */
    var body: Body? = null
        set(value) {
            if (value != null && _status == null) {
                _status = 200
            }
            field = value
        }

    /**
     * Any additional headers to send back to the client.
     */
    val headers = mutableMapOf<String, String>()

    /**
     * A holder of user data that can be added to this response.
     *
     * This will only be relevant for a project that uses an [ApiInterceptor]; in other words, this allows optional
     * communication from an API handler back to an API interceptor.
     */
    val data = MutableData()
}

fun Response.Body.Companion.stream(inputStream: InputStream, contentType: String = "application/octet-stream") =
    Response.Body(contentType) { inputStream.toByteSource() }

fun Response.Body.Companion.bytes(bytes: ByteArray, contentType: String = "application/octet-stream") =
    Response.Body(contentType) { RawByteSource(bytes) }

fun Response.Body.Companion.text(
    text: String,
    charset: Charset = Charsets.UTF_8,
    contentType: String = "text/plain; charset=${charset.name()}"
) = bytes(text.toByteArray(charset), contentType)

fun Response.Body.Companion.json(text: String, contentType: String = "application/json") =
    text(text, contentType = contentType)

/**
 * Convenience method for setting the body to a text value.
 */
@Deprecated("We introduced the `Response.Body` class so you should set `body` directly instead.", ReplaceWith("body = Response.Body.text(text)", "com.varabyte.kobweb.api.http.text"))
fun Response.setBodyText(text: String) {
    body = Response.Body.text(text)
}

/**
 * Set this to a response that tells the client that the requested resource has moved to a new location.
 *
 * @param status The specific redirect status code to use. See
 *   [MDN docs](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status) for more information. Defaults to
 *   307 (temporary redirect).
 *
 * @param isApiPath If true, [newPath] will be prefixed with the "/api" prefix (unless already prefixed). This is useful
 *   if you're redirecting the user away from one API endpoint to another. You can of course just prepend "/api" to the
 *   path yourself, but in most cases, Kobweb tries to hide the "/api" prefix from the user, so it's a bit strange for
 *   us to force them to manually reference it here. Therefore, this parameter is provided as a convenience and as a way
 *   to document this situation.
 */
fun Response.setAsRedirect(newPath: String, status: Int = 307, isApiPath: Boolean = false) {
    check(status in VALID_REDIRECT_STATUS_CODES) { "Redirect status code is invalid ($status); must be one of $VALID_REDIRECT_STATUS_CODES" }
    this.status = status
    headers["Location"] =
        if (!isApiPath || newPath.startsWith(API_PREFIX_WITH_TRAILING_SLASH)) newPath else "$API_PREFIX$newPath"
}
