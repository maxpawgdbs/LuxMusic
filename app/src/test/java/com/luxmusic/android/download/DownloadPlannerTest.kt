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
        assertEquals(DownloadService.TIKTOK, plan.attempts.single().requestService)
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
        assertEquals(DownloadService.SOUNDCLOUD, plan.attempts.single().requestService)
    }

    @Test
    fun `unsupported services get no plan`() {
        val plan = planner.createPlan(
            sourceUrl = "https://open.spotify.com/track/abc",
            sourceService = DownloadService.UNKNOWN,
            metadata = metadata,
            hasSession = false,
        )

        assertTrue(plan.attempts.isEmpty())
    }

    @Test
    fun `planner does not require metadata-only workflows anymore`() {
        assertFalse(planner.requiresMetadataBeforeDownload(DownloadService.YOUTUBE))
        assertFalse(planner.requiresMetadataBeforeDownload(DownloadService.UNKNOWN))
    }
}
