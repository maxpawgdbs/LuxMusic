package com.luxmusic.android.download

/** Updating Python code cannot repair a failed connection, a private post or a disk error. */
internal object ExtractorRecoveryPolicy {
    fun shouldUpdate(error: Throwable): Boolean {
        val text = generateSequence(error) { it.cause }.take(8)
            .joinToString(" ") { it.message.orEmpty() }.lowercase()
        if (listOf("timed out", "timeout", "connection", "network", "resolve host", "name resolution",
                "429", "403", "401", "404", "private", "login", "sign in", "sign-in", "cookies",
                "subscriber", "subscription", "premium", "drm", "geo-restricted", "not available",
                "disk", "space left", "permission denied", "read-only", "requested format is not available").any(text::contains)) return false
        return listOf("unable to extract", "extractor", "signature", "nsig", "javascript", "challenge",
            "player response").any(text::contains)
    }
}
