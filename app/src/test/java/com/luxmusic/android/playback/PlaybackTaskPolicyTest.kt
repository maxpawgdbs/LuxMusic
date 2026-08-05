package com.luxmusic.android.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTaskPolicyTest {
    @Test
    fun `playing queue keeps service after task removal`() {
        assertTrue(
            PlaybackTaskPolicy.shouldKeepService(
                hasMediaItems = true,
                playWhenReady = true,
                playbackEnded = false,
            ),
        )
    }

    @Test
    fun `paused queue stops service after task removal`() {
        assertFalse(
            PlaybackTaskPolicy.shouldKeepService(
                hasMediaItems = true,
                playWhenReady = false,
                playbackEnded = false,
            ),
        )
    }

    @Test
    fun `ended or empty queue stops service after task removal`() {
        assertFalse(
            PlaybackTaskPolicy.shouldKeepService(
                hasMediaItems = true,
                playWhenReady = true,
                playbackEnded = true,
            ),
        )
        assertFalse(
            PlaybackTaskPolicy.shouldKeepService(
                hasMediaItems = false,
                playWhenReady = true,
                playbackEnded = false,
            ),
        )
    }
}
