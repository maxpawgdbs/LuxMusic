package com.luxmusic.android

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luxmusic.android.playback.PlaybackSessionService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlaybackQueueInstrumentedTest {
    @Test(timeout = 60_000)
    fun queueContainsEveryTrackAndOnlyCurrentArtwork() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<LuxMusicApp>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val workspace = File(app.cacheDir, "queue-test-${UUID.randomUUID()}").apply { mkdirs() }
        val files = (1..3).map { File(workspace, "track-$it.wav").apply { writeBytes(wave()) } }
        val cover = File(workspace, "cover.jpg")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).let { bitmap ->
            cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            bitmap.recycle()
        }
        val tracks = app.libraryStore.importDownloadedFiles(files, null) { listOf(cover) }
        var controller: MediaController? = null
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            instrumentation.runOnMainSync { app.playbackGateway.playCollection(tracks, 1, "Тестовая очередь") }
            val state = withTimeout(15_000) {
                app.playbackGateway.state.first { it.currentTrackId == tracks[1].id && it.queueTrackIds.size == 3 }
            }
            assertEquals(tracks.map { it.id }.toSet(), state.queueTrackIds.toSet())
            controller = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackSessionService::class.java)))
                .buildAsync().get(15, TimeUnit.SECONDS)
            val connected = controller
            instrumentation.runOnMainSync {
                assertEquals(3, connected.mediaItemCount)
                assertEquals(1, (0 until connected.mediaItemCount).count {
                    connected.getMediaItemAt(it).mediaMetadata.artworkData != null
                })
            }
            instrumentation.runOnMainSync { app.playbackGateway.skipNext() }
            withTimeout(10_000) { app.playbackGateway.state.first { it.currentTrackId == tracks[2].id } }
        } finally {
            instrumentation.runOnMainSync {
                controller?.release()
                app.stopService(Intent(app, PlaybackSessionService::class.java))
            }
            activity.close()
            tracks.forEach { app.libraryStore.deleteTrack(it.id) }
            workspace.deleteRecursively()
        }
    }

    private fun wave(): ByteArray {
        val bytes = 8_000 * 2 * 30
        return ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + bytes); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8_000); putInt(16_000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(bytes)
        }.array()
    }
}
