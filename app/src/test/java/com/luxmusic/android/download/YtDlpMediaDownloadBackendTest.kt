package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpMediaDownloadBackendTest {
    @Test
    fun `youtube downloads video then extracts mp3 audio`() {
        val profile = YtDlpMediaDownloadBackend.requestProfileFor(DownloadService.YOUTUBE)

        assertEquals("bestvideo*+bestaudio/best", profile.formatSelector)
        assertTrue(profile.extractAudio)
        assertEquals("mp3", profile.targetAudioExtension)
    }

    @Test
    fun `soundcloud keeps direct audio profile`() {
        val profile = YtDlpMediaDownloadBackend.requestProfileFor(DownloadService.SOUNDCLOUD)

        assertEquals(
            "bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio[acodec!=none]/best[acodec!=none]/bestaudio/best",
            profile.formatSelector,
        )
        assertFalse(profile.extractAudio)
        assertEquals(null, profile.targetAudioExtension)
    }
}
