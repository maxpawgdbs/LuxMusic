package com.luxmusic.android.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmusic.android.data.DownloadService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DownloadRequestInstrumentedTest {
    @Test fun nativeRequestsUseBoundedFastAudioOptions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backend = YtDlpMediaDownloadBackend(context)
        DownloadPlatformPolicy.directServices.filter { it != DownloadService.DIRECT_FILE }.forEach { service ->
            val request = backend.buildDownloadRequest("https://example.com/video", context.cacheDir, service, null)
            assertEquals("8", request.getOption("--concurrent-fragments"))
            assertEquals("256K", request.getOption("--buffer-size"))
            assertEquals("12", request.getOption("--socket-timeout"))
            assertEquals("2", request.getOption("--retries"))
            assertEquals("0", request.getOption("--sleep-requests"))
            assertEquals("best", request.getOption("--audio-format"))
            assertEquals("!is_live", request.getOption("--match-filter"))
            assertTrue(request.hasOption("--no-playlist"))
            assertTrue(request.hasOption("--extract-audio"))
            assertFalse(request.hasOption("--cookies"))
        }
    }

    @Test fun youtubeIgnoresLegacyCookieSessions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "request-only-${java.util.UUID.randomUUID()}")
        val request = YtDlpMediaDownloadBackend(context).buildDownloadRequest(
            "https://youtube.com/watch?v=test", directory, DownloadService.YOUTUBE,
            DownloadSession("test-cookie", "test-user-agent"))
        assertFalse(request.hasOption("--cookies"))
        assertFalse(request.hasOption("--user-agent"))
        assertFalse(directory.exists())
    }
}
