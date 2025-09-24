package com.varabyte.kobweb.api.http

import kotlinx.coroutines.flow.Flow
import java.io.Closeable

interface Multipart {
    companion object {
        fun isMultipartContentType(contentType: String) = contentType.startsWith("multipart/", ignoreCase = true)
    }

    interface Part : ContentSource, Closeable {
        override val contentLength: Long? get() = null

        val headers: Map<String, List<String>>
        val contentDisposition: ContentDisposition?
        val name: String?
        val extras: Extras?
    }

    sealed interface Extras {
        class File(val originalFileName: String?) : Extras
    }

    val parts: Flow<Part>
}

/**
 * @param autoClose Whether to automatically call [Multipart.Part.close] after this callback is triggered.
 */
class MultipartScope internal constructor(var autoClose: Boolean)

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
