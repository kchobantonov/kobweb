package com.varabyte.kobweb.api.http

import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.data.Data
import com.varabyte.kobweb.api.data.MutableData
import com.varabyte.kobweb.api.intercept.ApiInterceptor
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Information passed into an API endpoint from the client.
 *
 * The request information will be passed in via an [ApiContext]. Developers implementing an API endpoint can read
 * request values with code like the following:
 *
 * ```
 * @Api
 * fun echo(ctx: ApiContext) {
 *   val msg = ctx.req.params["msg"]
 *   if (msg != null) {
 *     ctx.res.setBodyText("Received message: $msg")
 *   }
 *   else {
 *     ctx.res.status = 400
 *     ctx.res.setBodyText("Missing: required parameter 'msg'")
 *   }
 * }
 * ```
 *
 * @see Response
 */
interface Request {
    class Body private constructor(val contentType: String, private val dataProvider: DataProvider) {
        private sealed interface DataProvider {
            class Stream(val provideStream: suspend () -> InputStream) : DataProvider
        }

        constructor(contentType: String, produceStream: suspend () -> InputStream) : this(
            contentType,
            DataProvider.Stream(produceStream)
        )

        /**
         * Fetch an [InputStream] associated with this request body.
         *
         * This should only be called once per request body.
         *
         * You should remember to call [InputStream.use] on the returned stream or at least [InputStream.close].
         */
        suspend fun stream(): InputStream {
            @Suppress("REDUNDANT_ELSE_IN_WHEN") // Will be adding more branches in a followup commit
            return when (val dataProvider = dataProvider) {
                is DataProvider.Stream -> dataProvider.provideStream()
                else -> throw IllegalStateException("The body of this request does not support being queried as a stream.")
            }
        }
    }

    /** Information about the connection that carried the request. */
    val connection: Connection
    /** The type of http method this call was sent with. */
    val method: HttpMethod
    /**
     * A list of key/value pairs extracted either from the user's [query string](https://en.wikipedia.org/wiki/Query_string)
     * or from any dynamic path parts.
     */
    val params: Map<String, String>
    /**
     * Like [params] but only for the query string.
     *
     * This is provided just in case a user needs to disambiguate between a dynamic path part and a query parameter with
     * the same name.
     */
    val queryParams: Map<String, String>
    /** All headers sent with the request. */
    val headers: Map<String, List<String>>
    /**
     * Any cookies sent with the request.
     *
     * Note the value of the cookies will be in a raw format, so you may need to decode them yourself.
     */
    val cookies: Map<String, String>
    /**
     * A holder of user data that can be added to this request.
     *
     * This will only be relevant for a project that uses an [ApiInterceptor]; in other words, this allows optional
     * communication from an API interceptor to an API interceptor (such as storing some calculated auth value).
     */
    val data: Data
    /**
     * The body payload sent with the request.
     *
     * Will only potentially be set with appropriate methods that are allowed to send data, i.e. [HttpMethod.POST],
     * [HttpMethod.PUT], and [HttpMethod.PATCH]. This value will be null if no body is set / applicable.
     */
    val body: Body?

    /**
     * Top-level container class for views about a connection for some request.
     *
     * You may wish to
     * review [Ktor docs about Forwarding Headers](https://ktor.io/docs/forward-headers.html#request_info) if you want
     * to learn more about how the [origin] and [local] views can be different.
     *
     * @property origin Details about the request's connection point of origin (i.e. the client).
     * @property local Details about the request's connection at the point it was received by the server. This can be
     *   different from [origin] if the server is behind a proxy (that is, the request was intercepted and rerouted), at
     *   which point the connection details will be about the proxy, not the client.
     */
    class Connection(
        val origin: Details,
        val local: Details,
    ) {
        /**
         * Details about a connection that carries a request.
         *
         * @property scheme The scheme of the connection, e.g. "http" or "https"
         * @property version The version of the connection, e.g. "HTTP/1.1"
         * @property localAddress The IP address of the client making the request.
         * @property localHost The host name of the client making the request.
         * @property localPort The port of the client making the request.
         * @property remoteAddress The IP address of the server receiving the request.
         * @property remoteHost The host name of the server receiving the request.
         * @property remotePort The port of the server receiving the request.
         * @property serverHost The host name of the server receiving the request. This can be different from the
         *   remote host in cases where proxies or load balancers are used.
         * @property serverPort The port of the server receiving the request. This can be different from the remote port
         *   in cases where proxies or load balancers are used.
         */
        data class Details(
            val scheme: String,
            val version: String,
            val localAddress: String,
            val localHost: String,
            val localPort: Int,
            val remoteAddress: String,
            val remoteHost: String,
            val remotePort: Int,
            val serverHost: String,
            val serverPort: Int,
        )
    }
}

class MutableRequest(
    override val connection: Request.Connection,
    override var method: HttpMethod,
    params: Map<String, String>,
    queryParams: Map<String, String>,
    headers: Map<String, List<String>>,
    cookies: Map<String, String>,
    override var body: Request.Body?,
    override val data: MutableData = MutableData(),
) : Request {
    constructor(request: Request) : this(
        request.connection,
        request.method,
        request.params,
        request.queryParams,
        request.headers,
        request.cookies,
        request.body,
        request.data.toMutableData(),
    )

    override val params: MutableMap<String, String> = params.toMutableMap()
    override val queryParams: MutableMap<String, String> = queryParams.toMutableMap()
    override val headers: MutableMap<String, MutableList<String>> = headers
        .mapValues { entry -> entry.value.toMutableList() }
        .toMutableMap()

    override val cookies: MutableMap<String, String> = cookies.toMutableMap()
}

/**
 * Convenience method to convert a body stream() into a UTF-8 string.
 */
suspend fun Request.Body.text(charset: Charset = contentType.parseCharsetFromContentType()): String {
    return stream().use { it.readAllBytes().toString(charset) }
}

@Deprecated("Use `req.body?.text()` instead", ReplaceWith("body?.text()"))
suspend fun Request.readBodyText(): String? {
    return body?.text()
}
