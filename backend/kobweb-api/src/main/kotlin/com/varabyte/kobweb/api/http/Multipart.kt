package com.varabyte.kobweb.api.http

import kotlinx.coroutines.flow.Flow
import java.io.Closeable

/**
 * Represents all relevant information associated with a [multipart request](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Methods/POST#multipart_form_submission).
 *
 * The general shape of processing a multipart request in your API method is like so:
 * ```
 * @Api
 * suspend fun multipart(ctx: ApiContext) {
 *     if (ctx.req.method != HttpMethod.POST) return
 *     val mp = ctx.req.body?.multipart() ?: return
 *
 *     mp.forEachPart { part ->
 *         // Here, part.openContent() gives you a ByteSource you can use to stream the content information.
 *         // If you are sure that the content is fairly limited in size, you can use `part.bytes()` or `part.text()`
 *         // instead to read everything directly.
 *
 *         // Also, if you sent file data, you can use
 *         // (part.extras as? Multipart.Part.Extras.File)?.originalFileName
 *         // to get the original file name uploaded by the user.
 *     })
 * }
 * ```
 */
interface Multipart {
    companion object {
        fun isMultipartContentType(contentType: String) = contentType.startsWith("multipart/", ignoreCase = true)
    }

    /**
     * One section inside the parent [Multipart] request body.
     */
    interface Part : ContentSource, Closeable {
        override val contentLength: Long? get() = null

        val headers: Map<String, List<String>>
        val contentDisposition: ContentDisposition?
        val name: String?
        val extras: Extras?
    }

    /**
     * Extra values beyond the common set, provided specifically based the type of part that we are dealing with.
     */
    // NOTE: Only File for now. But we use a sealed interface to future proof this API; the upstream ktor API uses its
    // own sealed interface to split between the four types of incoming parts so it seemed useful that we might do the
    // same.
    sealed interface Extras {
        class File(val originalFileName: String?) : Extras
    }

    /**
     * The list of all parts in this multipart request.
     *
     * You can either collect it directly or use [forEachPart] which is provided as a convenience method.
     *
     * If you collect it yourself, be sure to [close][Part.close] each part when you're done with it.
     */
    val parts: Flow<Part>
}

/**
 * @param autoClose Whether to automatically call [Part.close][Multipart.Part.close] after the callback this scope is
 *   associated with is finished.
 */
class MultipartScope internal constructor(var autoClose: Boolean)

/**
 * A convenience method that wraps [Multipart.parts] so you don't have to collect it yourself.
 *
 * @param autoClose If true, automatically call [Part.close][Multipart.Part.close] after each part is handled. You can
 *   override [MultipartScope.autoClose] as well per part, if you need to override this value on a case-by-case basis.
 */
suspend fun Multipart.forEachPart(autoClose: Boolean = true, block: suspend MultipartScope.(Multipart.Part) -> Unit) {
    parts.collect { part ->
        val scope = MultipartScope(autoClose)
        try {
            scope.block(part)
        } finally {
            if (scope.autoClose) {
                part.close()
            }
        }
    }
}
