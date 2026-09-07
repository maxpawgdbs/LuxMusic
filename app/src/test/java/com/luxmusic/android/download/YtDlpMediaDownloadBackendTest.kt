package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpMediaDownloadBackendTest {
    @Test
    fun `extractor updates use bounded native requests without a media URL`() {
        for (channel in ExtractorChannel.entries) {
            val request = YtDlpMediaDownloadBackend.buildUpdateRequest(channel)
            assertEquals(if (channel == ExtractorChannel.NIGHTLY) "nightly" else "stable", request.getOption("--update-to"))
            assertEquals("15", request.getOption("--socket-timeout"))
            assertEquals("1", request.getOption("--retries"))
            assertTrue(request.hasOption("--ignore-config"))
            assertFalse(request.buildCommand().any { it.startsWith("http") })
        }
    }

    @Test
    fun `youtube extracts audio even when only a combined stream is available`() {
        val profile = YtDlpMediaDownloadBackend.requestProfileFor(DownloadService.YOUTUBE)

        assertTrue(profile.formatSelector.startsWith("bestaudio"))
        assertFalse(profile.formatSelector.contains("bestvideo"))
        assertTrue(profile.formatSelector.contains("best[acodec!=?none]"))
        assertTrue(profile.extractAudio)
        assertEquals("best", profile.targetAudioExtension)
    }

    @Test
    fun `soundcloud keeps direct audio profile`() {
        val profile = YtDlpMediaDownloadBackend.requestProfileFor(DownloadService.SOUNDCLOUD)

        assertEquals(
            "bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio[ext=opus]/bestaudio[ext=webm]/bestaudio/best[height<=480][acodec!=?none]/best[acodec!=?none]",
            profile.formatSelector,
        )
        assertTrue(profile.extractAudio)
        assertEquals("best", profile.targetAudioExtension)
    }

    @Test
    fun `tiktok extracts audio without forced transcoding`() {
        val profile = YtDlpMediaDownloadBackend.requestProfileFor(DownloadService.TIKTOK)

        assertTrue(profile.extractAudio)
        assertEquals("best", profile.targetAudioExtension)
    }

    @Test
    fun `direct media with unknown codec is probed by ffmpeg instead of rejected`() {
        val profile = YtDlpMediaDownloadBackend.requestProfileFor(DownloadService.UNKNOWN)
        assertTrue(profile.formatSelector.contains("[acodec!=?none]"))
        assertTrue(profile.extractAudio)
    }
}
