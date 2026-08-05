package com.luxmusic.android.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.luxmusic.android.LuxMusicApp

/**
 * Keeps the Media3 session alive independently from the activity and lets Android render the
 * system media card, lock-screen controls, artwork, progress and seek UI.
 */
@UnstableApi
class PlaybackSessionService : MediaSessionService() {
    override fun onCreate() {
        super.onCreate()
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS)
        addSession(playbackController().mediaSession())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return playbackController().mediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        return if (playbackController().hasMediaItems()) START_STICKY else result
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the activity away must not affect playback. The foreground MediaSessionService
        // remains the owner of the player until the user stops it from system media controls.
    }

    override fun onDestroy() {
        removeSession(playbackController().mediaSession())
        playbackController().onSessionServiceStopped()
        super.onDestroy()
    }

    private fun playbackController(): PlaybackController {
        return (application as LuxMusicApp).playbackController
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackSessionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackSessionService::class.java))
        }
    }
}
