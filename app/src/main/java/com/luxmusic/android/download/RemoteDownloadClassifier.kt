package com.luxmusic.android.download

import com.luxmusic.android.data.ImportFileRules
import java.net.URI

internal object RemoteDownloadClassifier {
    fun isArchiveUrl(value: String): Boolean {
        val path = runCatching { URI(value).path.orEmpty() }.getOrDefault("")
        return path.endsWith(".zip", ignoreCase = true)
    }

    fun isArchiveResponse(contentType: String?, contentDisposition: String?): Boolean {
        val normalizedType = contentType.orEmpty().substringBefore(';').trim().lowercase()
        if (normalizedType in ZIP_CONTENT_TYPES) return true
        val fileName = contentDisposition.orEmpty().lowercase()
        return Regex("(?:filename|filename\\*)\\s*=\\s*(?:utf-8''|\")?[^;\"]+\\.zip(?:\"|;|$)")
            .containsMatchIn(fileName)
    }

    fun hasZipSignature(header: ByteArray): Boolean = ImportFileRules.hasZipSignature(header)

    private val ZIP_CONTENT_TYPES = setOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/x-zip",
    )
}
