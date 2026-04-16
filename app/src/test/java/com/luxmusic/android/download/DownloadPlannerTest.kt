package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPlannerTest {
    private val planner = DownloadPlanner()
    private val metadata = DownloadSourceMetadata(
        title = "Track Name",
        artist = "Artist Name",
        durationMs = 180_000,
        queryHint = "Artist Name Track Name",
    )

    @Test
    fun `youtube uses direct extractor only`() {
        val plan = planner.createPlan(
            sourceUrl = "https://youtu.be/demo",
            sourceService = DownloadService.YOUTUBE,
            metadata = metadata,
            hasSession = false,
        )

        assertEquals(1, plan.attempts.size)
        assertEquals(DownloadAttemptKind.DIRECT, plan.attempts.single().kind)
        assertEquals(DownloadService.YOUTUBE, plan.attempts.single().requestService)
    }

    @Test
    fun `tiktok uses direct extractor only`() {
        val plan = planner.createPlan(
            sourceUrl = "https://www.tiktok.com/@artist/video/1",
            sourceService = DownloadService.TIKTOK,
            metadata = metadata,
            hasSession = false,
        )

        assertEquals(1, plan.attempts.size)
        assertEquals(DownloadAttemptKind.DIRECT, plan.attempts.single().kind)
    }

    @Test
    fun `soundcloud uses direct extractor only`() {
        val plan = planner.createPlan(
            sourceUrl = "https://soundcloud.com/artist/track",
            sourceService = DownloadService.SOUNDCLOUD,
            metadata = metadata,
            hasSession = false,
        )

        assertEquals(1, plan.attempts.size)
        assertEquals(DownloadAttemptKind.DIRECT, plan.attempts.single().kind)
    }

    @Test
    fun `apple music uses metadata fallback only`() {
        val plan = planner.createPlan(
            sourceUrl = "https://music.apple.com/us/album/song-name/1712345678?i=1712345680",
            sourceService = DownloadService.APPLE_MUSIC,
            metadata = metadata,
            hasSession = false,
        )

        assertEquals(1, plan.attempts.size)
        assertEquals(DownloadAttemptKind.MATCHED_SEARCH, plan.attempts.single().kind)
        assertTrue(planner.requiresMetadataBeforeDownload(DownloadService.APPLE_MUSIC))
    }

    @Test
    fun `spotify uses metadata fallback only`() {
        val plan = planner.createPlan(
            sourceUrl = "https://open.spotify.com/track/abc",
            sourceService = DownloadService.SPOTIFY,
            metadata = metadata,
            hasSession = false,
        )

        assertEquals(1, plan.attempts.size)
        assertEquals(DownloadAttemptKind.MATCHED_SEARCH, plan.attempts.single().kind)
        assertTrue(planner.requiresMetadataBeforeDownload(DownloadService.SPOTIFY))
    }

    @Test
    fun `yandex music keeps direct attempt and fallback`() {
        val plan = planner.createPlan(
            sourceUrl = "https://music.yandex.ru/album/1/track/2",
            sourceService = DownloadService.YANDEX_MUSIC,
            metadata = metadata,
            hasSession = false,
        )

        assertEquals(2, plan.attempts.size)
        assertEquals(DownloadAttemptKind.DIRECT, plan.attempts[0].kind)
        assertEquals(DownloadAttemptKind.MATCHED_SEARCH, plan.attempts[1].kind)
    }

    @Test
    fun `vk music keeps direct attempt and fallback`() {
        val plan = planner.createPlan(
            sourceUrl = "https://vk.com/audio-1_2",
            sourceService = DownloadService.VK_MUSIC,
            metadata = metadata,
            hasSession = false,
        )

        assertEquals(2, plan.attempts.size)
        assertEquals(DownloadAttemptKind.DIRECT, plan.attempts[0].kind)
        assertEquals(DownloadAttemptKind.MATCHED_SEARCH, plan.attempts[1].kind)
    }

    @Test
    fun `planner requires metadata for apple but not for youtube`() {
        assertTrue(planner.requiresMetadataBeforeDownload(DownloadService.APPLE_MUSIC))
        assertFalse(planner.requiresMetadataBeforeDownload(DownloadService.YOUTUBE))
    }
}
