package com.luxmusic.android.playback

import java.io.File

/**
 * Loads artwork into MediaMetadata instead of exposing a private file:// URI to System UI.
 * The strict size limit also keeps metadata safely below Android's Binder transaction limit.
 */
internal object PlaybackArtwork {
    internal const val MAX_BYTES = 512 * 1024L

    fun read(path: String?): ByteArray? {
        val file = path?.takeIf(String::isNotBlank)?.let(::File) ?: return null
        if (!file.isFile || file.length() !in 1..MAX_BYTES) return null
        return runCatching {
            file.readBytes().takeIf { it.isNotEmpty() && it.size <= MAX_BYTES }
        }.getOrNull()
    }
}
