package com.luxmusic.android.download

import java.util.concurrent.atomic.AtomicBoolean

/** Prevents overlapping native yt-dlp/FFmpeg and archive operations. */
internal class DownloadOperationGuard {
    private val occupied = AtomicBoolean(false)

    fun tryAcquire(): Boolean = occupied.compareAndSet(false, true)

    fun release() {
        occupied.set(false)
    }
}

/** Keeps native downloader callbacks from triggering hundreds of UI recompositions per second. */
internal class DownloadProgressLimiter(
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val minimumIntervalMs: Long = 250L,
) {
    private var lastPublishedAtMs = Long.MIN_VALUE
    private var publishedOnce = false

    @Synchronized
    fun shouldPublish(progress: Float): Boolean {
        val now = clockMs()
        val enoughTimePassed = !publishedOnce || now - lastPublishedAtMs >= minimumIntervalMs
        val completed = progress >= 0.999f
        if (!enoughTimePassed && !completed) return false
        publishedOnce = true
        lastPublishedAtMs = now
        return true
    }
}
