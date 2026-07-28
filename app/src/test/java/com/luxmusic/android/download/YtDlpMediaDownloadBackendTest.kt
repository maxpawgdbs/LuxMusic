package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpMediaDownloadBackendTest {
    @Test
    fun `youtube downloads audio only without conversion`() {
        val profile = YtDlpMediaDownloadBackend.requestProfileFor(DownloadService.YOUTUBE)

        assertTrue(profile.formatSelector.startsWith("bestaudio"))
        assertFalse(profile.formatSelector.contains("bestvideo"))
        assertFalse(profile.extractAudio)
        assertEquals(null, profile.targetAudioExtension)
    }

    @Test
    fun `soundcloud keeps direct audio profile`() {
        val profile = YtDlpMediaDownloadBackend.requestProfileFor(DownloadService.SOUNDCLOUD)

        assertEquals(
            "bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio[ext=opus]/bestaudio[ext=webm]/bestaudio/best[acodec!=none]",
            profile.formatSelector,
        )
        assertFalse(profile.extractAudio)
        assertEquals(null, profile.targetAudioExtension)
    }

    @Test
    fun `tiktok extracts audio without forced transcoding`() {
        val profile = YtDlpMediaDownloadBackend.requestProfileFor(DownloadService.TIKTOK)

        assertTrue(profile.extractAudio)
        assertEquals("best", profile.targetAudioExtension)
    }
}
