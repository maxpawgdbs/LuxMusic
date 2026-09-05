package com.luxmusic.android.playback

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmusic.android.data.Track
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlaybackGatewayInstrumentedTest {
    @Test fun pausedQueueRestorationDoesNotRequireForegroundPlayback() = withContext { context ->
        context.preferences.edit().putString("queue_ids", "saved-track")
            .putBoolean("play_when_ready", false).commit()
        PlaybackGateway(context).restorePlayback()
        assertEquals(PlaybackSessionService.ACTION_RESTORE, context.started.single().action)
    }

    @Test fun emptyQueueDoesNotStartAService() = withContext { context ->
        PlaybackGateway(context).restorePlayback()
        assertTrue(context.started.isEmpty())
    }

    @Test fun playRequestDoesNotPromiseThatAnUnavailableFileWillStart() = withContext { context ->
        val track = Track(id = "missing", title = "Missing", artist = "Test", album = "Test",
            durationMs = 0, localPath = "/missing-audio.wav", importedAt = 0)
        PlaybackGateway(context).playCollection(listOf(track), 0, "Test")
        assertEquals(PlaybackSessionService.ACTION_PLAY_COLLECTION, context.started.single().action)
    }

    private fun withContext(test: (RecordingContext) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val preferencesName = "gateway-test-${UUID.randomUUID()}"
        val context = RecordingContext(app, app.getSharedPreferences(preferencesName, Context.MODE_PRIVATE))
        try { test(context) } finally { app.deleteSharedPreferences(preferencesName) }
    }

    private class RecordingContext(base: Context, val preferences: SharedPreferences) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
        override fun startService(service: Intent): ComponentName? {
            started += service
            return service.component
        }
        override fun startForegroundService(service: Intent): ComponentName? {
            throw AssertionError("A UI command must let Media3 promote actual playback, not force foreground startup")
        }
    }
}
