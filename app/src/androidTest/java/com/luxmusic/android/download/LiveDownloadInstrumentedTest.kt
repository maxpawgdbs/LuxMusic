package com.luxmusic.android.download

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luxmusic.android.LuxMusicApp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Optional network smoke test: pass -e liveDownloadUrl <public URL> to am instrument. */
@RunWith(AndroidJUnit4::class)
class LiveDownloadInstrumentedTest {
    @Test fun downloadsPublicAudioIntoLibrary() = runBlocking {
        val url = InstrumentationRegistry.getArguments().getString("liveDownloadUrl").orEmpty()
        assumeTrue(url.isNotBlank())
        val app = ApplicationProvider.getApplicationContext<LuxMusicApp>()
        val expectedProxy = InstrumentationRegistry.getArguments().getString("liveExpectedProxy")
        if (expectedProxy != null) assertEquals(expectedProxy, SystemDownloadProxy.forUrl(url))
        val started = android.os.SystemClock.elapsedRealtime()
        val result = withTimeout(180_000) { app.linkDownloader.downloadCollection(url) }
        assertTrue(app.linkDownloader.state.value.errorMessage ?: result.exceptionOrNull()?.message, result.isSuccess)
        val tracks = result.getOrThrow().tracks
        try {
            assertTrue(tracks.isNotEmpty())
            assertTrue(tracks.all { File(it.localPath).length() > 0 && it.durationMs > 0 })
            println("Live download OK: elapsedMs=${android.os.SystemClock.elapsedRealtime() - started}, " +
                "durationMs=${tracks.map { it.durationMs }}, bytes=${tracks.map { File(it.localPath).length() }}")
        } finally { tracks.forEach { app.libraryStore.deleteTrack(it.id) } }
    }
}
