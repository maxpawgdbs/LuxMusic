package com.luxmusic.android.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRuntimePolicyTest {
    @Test
    fun `operation guard rejects overlap and can be reused`() {
        val guard = DownloadOperationGuard()

        assertTrue(guard.tryAcquire())
        assertFalse(guard.tryAcquire())
        guard.release()
        assertTrue(guard.tryAcquire())
    }

    @Test
    fun `progress limiter publishes at most four regular updates per second`() {
        var now = 1_000L
        val limiter = DownloadProgressLimiter(clockMs = { now }, minimumIntervalMs = 250L)

        assertTrue(limiter.shouldPublish(0.01f))
        repeat(50) { assertFalse(limiter.shouldPublish(0.4f)) }
        now += 249L
        assertFalse(limiter.shouldPublish(0.5f))
        now += 1L
        assertTrue(limiter.shouldPublish(0.6f))
    }

    @Test
    fun `progress limiter never hides completion`() {
        val limiter = DownloadProgressLimiter(clockMs = { 42L })

        assertTrue(limiter.shouldPublish(0.2f))
        assertFalse(limiter.shouldPublish(0.9f))
        assertTrue(limiter.shouldPublish(1f))
    }
}
