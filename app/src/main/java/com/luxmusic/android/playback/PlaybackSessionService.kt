package com.luxmusic.android.playback

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.luxmusic.android.LuxMusicApp
import com.luxmusic.android.R
import com.luxmusic.android.data.Track

/**
 * The sole owner of LuxMusic's Player and MediaSession. Media3 publishes the foreground
 * MediaStyle notification with the active session token, metadata, playback state and actions.
 */
@UnstableApi
class PlaybackSessionService : MediaSessionService() {
    private lateinit var playbackController: PlaybackController

    private val luxApp: LuxMusicApp
        get() = application as LuxMusicApp

    override fun onCreate() {
        super.onCreate()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.playback_notification_channel)
            .build()
            .apply { setSmallIcon(R.drawable.ic_notification) }
        setMediaNotificationProvider(notificationProvider)
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS)

        playbackController = PlaybackController(
            context = this,
            libraryStore = luxApp.libraryStore,
            stateSink = luxApp.playbackGateway::publish,
        )
        addSession(playbackController.mediaSession())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return playbackController.mediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        handleCommand(intent)
        if (!playbackController.hasMediaItems()) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (playbackController.shouldRemainWhenTaskRemoved()) return

        playbackController.stopPlayback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (::playbackController.isInitialized) {
            removeSession(playbackController.mediaSession())
            playbackController.release()
        }
        super.onDestroy()
    }

    private fun handleCommand(intent: Intent?) {
        when (intent?.action) {
            ACTION_PLAY_COLLECTION -> playCollection(intent)
            ACTION_TOGGLE -> playbackController.togglePlayback()
            ACTION_NEXT -> playbackController.skipNext()
            ACTION_PREVIOUS -> playbackController.skipPrevious()
            ACTION_TOGGLE_SHUFFLE -> playbackController.toggleShuffle()
            ACTION_CYCLE_REPEAT -> playbackController.cycleRepeatMode()
            ACTION_SEEK -> playbackController.seekToFraction(
                intent.getFloatExtra(EXTRA_SEEK_FRACTION, 0f),
            )
            ACTION_SELECT_TRACK -> intent.getStringExtra(EXTRA_TRACK_ID)
                ?.let(playbackController::selectQueueTrack)
            ACTION_UPDATE_TRACK -> intent.getStringExtra(EXTRA_TRACK_ID)
                ?.let(::updateTrack)
            ACTION_UPDATE_QUEUE_TITLE -> {
                val previousTitle = intent.getStringExtra(EXTRA_PREVIOUS_QUEUE_TITLE)
                val newTitle = intent.getStringExtra(EXTRA_QUEUE_TITLE)
                if (previousTitle != null && newTitle != null) {
                    playbackController.updateQueueTitle(previousTitle, newTitle)
                }
            }
            ACTION_CLEAR_ACTIVE_PLAYLIST -> intent.getStringExtra(EXTRA_PLAYLIST_ID)
                ?.let(playbackController::clearActivePlaylist)
            ACTION_REMOVE_TRACK -> intent.getStringExtra(EXTRA_TRACK_ID)
                ?.let(playbackController::removeTrack)
        }
    }

    private fun playCollection(intent: Intent) {
        val requestedIds = intent.getStringArrayListExtra(EXTRA_TRACK_IDS).orEmpty()
        val tracksById = luxApp.libraryStore.snapshot.value.tracks.associateBy(Track::id)
        val queue = requestedIds.mapNotNull(tracksById::get)
        val startTrackId = intent.getStringExtra(EXTRA_START_TRACK_ID)
        val startIndex = queue.indexOfFirst { it.id == startTrackId }
        if (queue.isEmpty() || startIndex < 0) {
            stopSelf()
            return
        }
        playbackController.playOrToggleCollection(
            tracks = queue,
            startIndex = startIndex,
            queueTitle = intent.getStringExtra(EXTRA_QUEUE_TITLE).orEmpty().ifBlank { "Библиотека" },
            playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID),
        )
    }

    private fun updateTrack(trackId: String) {
        luxApp.libraryStore.snapshot.value.tracks
            .firstOrNull { it.id == trackId }
            ?.let(playbackController::updateTrackDetails)
    }

    companion object {
        internal const val ACTION_PLAY_COLLECTION = "com.luxmusic.android.action.PLAY_COLLECTION"
        internal const val ACTION_TOGGLE = "com.luxmusic.android.action.TOGGLE"
        internal const val ACTION_NEXT = "com.luxmusic.android.action.NEXT"
        internal const val ACTION_PREVIOUS = "com.luxmusic.android.action.PREVIOUS"
        internal const val ACTION_TOGGLE_SHUFFLE = "com.luxmusic.android.action.TOGGLE_SHUFFLE"
        internal const val ACTION_CYCLE_REPEAT = "com.luxmusic.android.action.CYCLE_REPEAT"
        internal const val ACTION_SEEK = "com.luxmusic.android.action.SEEK"
        internal const val ACTION_SELECT_TRACK = "com.luxmusic.android.action.SELECT_TRACK"
        internal const val ACTION_UPDATE_TRACK = "com.luxmusic.android.action.UPDATE_TRACK"
        internal const val ACTION_UPDATE_QUEUE_TITLE = "com.luxmusic.android.action.UPDATE_QUEUE_TITLE"
        internal const val ACTION_CLEAR_ACTIVE_PLAYLIST = "com.luxmusic.android.action.CLEAR_ACTIVE_PLAYLIST"
        internal const val ACTION_REMOVE_TRACK = "com.luxmusic.android.action.REMOVE_TRACK"

        internal const val EXTRA_TRACK_IDS = "track_ids"
        internal const val EXTRA_TRACK_ID = "track_id"
        internal const val EXTRA_START_TRACK_ID = "start_track_id"
        internal const val EXTRA_QUEUE_TITLE = "queue_title"
        internal const val EXTRA_PREVIOUS_QUEUE_TITLE = "previous_queue_title"
        internal const val EXTRA_PLAYLIST_ID = "playlist_id"
        internal const val EXTRA_SEEK_FRACTION = "seek_fraction"

        private const val NOTIFICATION_CHANNEL_ID = "luxmusic_playback"
    }
}
