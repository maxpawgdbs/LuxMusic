package com.luxmusic.android.playback

import com.luxmusic.android.data.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackModePolicyTest {
    @Test
    fun `shuffle defaults to infinite queue repeat`() {
        assertEquals(
            RepeatMode.ALL,
            PlaybackModePolicy.repeatAfterShuffleEnabled(RepeatMode.NONE),
        )
    }

    @Test
    fun `shuffle preserves single track repeat`() {
        assertEquals(
            RepeatMode.ONE,
            PlaybackModePolicy.repeatAfterShuffleEnabled(RepeatMode.ONE),
        )
    }

    @Test
    fun `repeat toggles between queue and one while shuffled`() {
        assertEquals(
            RepeatMode.ONE,
            PlaybackModePolicy.nextRepeat(RepeatMode.ALL, shuffleEnabled = true),
        )
        assertEquals(
            RepeatMode.ALL,
            PlaybackModePolicy.nextRepeat(RepeatMode.ONE, shuffleEnabled = true),
        )
    }

    @Test
    fun `repeat keeps full cycle without shuffle`() {
        assertEquals(
            RepeatMode.ALL,
            PlaybackModePolicy.nextRepeat(RepeatMode.NONE, shuffleEnabled = false),
        )
        assertEquals(
            RepeatMode.ONE,
            PlaybackModePolicy.nextRepeat(RepeatMode.ALL, shuffleEnabled = false),
        )
        assertEquals(
            RepeatMode.NONE,
            PlaybackModePolicy.nextRepeat(RepeatMode.ONE, shuffleEnabled = false),
        )
    }
}
