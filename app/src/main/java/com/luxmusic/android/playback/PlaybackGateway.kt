package com.luxmusic.android.playback

import android.content.Context
import android.content.Intent
import com.luxmusic.android.data.PlaybackState
import com.luxmusic.android.AppMessages
import com.luxmusic.android.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI-side command gateway. It deliberately owns no Player or MediaSession; both live exclusively
 * inside [PlaybackSessionService].
 */
class PlaybackGateway(context: Context, private val messages: AppMessages = AppMessages()) {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(PlaybackState())

    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    fun restorePlayback() {
        messages.attempt("Не удалось восстановить воспроизведение.") {
            if (PlaybackController.hasPersistedQueue(appContext)) {
                send(action = PlaybackSessionService.ACTION_RESTORE)
            }
        }
    }

    fun playCollection(
        tracks: List<Track>,
        startIndex: Int,
        queueTitle: String,
        playlistId: String? = null,
    ) {
        if (tracks.isEmpty() || startIndex !in tracks.indices) return
        send(
            action = PlaybackSessionService.ACTION_PLAY_COLLECTION,
        ) {
            putStringArrayListExtra(
                PlaybackSessionService.EXTRA_TRACK_IDS,
                ArrayList(tracks.map(Track::id)),
            )
            putExtra(PlaybackSessionService.EXTRA_START_TRACK_ID, tracks[startIndex].id)
            putExtra(PlaybackSessionService.EXTRA_QUEUE_TITLE, queueTitle)
            putExtra(PlaybackSessionService.EXTRA_PLAYLIST_ID, playlistId)
        }
    }

    fun togglePlayback() = sendForActiveQueue(PlaybackSessionService.ACTION_TOGGLE)

    fun skipNext() = sendForActiveQueue(PlaybackSessionService.ACTION_NEXT)

    fun skipPrevious() = sendForActiveQueue(PlaybackSessionService.ACTION_PREVIOUS)

    fun toggleShuffle() = sendForActiveQueue(PlaybackSessionService.ACTION_TOGGLE_SHUFFLE)

    fun cycleRepeatMode() = sendForActiveQueue(PlaybackSessionService.ACTION_CYCLE_REPEAT)

    fun seekToFraction(fraction: Float) =
        sendForActiveQueue(PlaybackSessionService.ACTION_SEEK) {
            putExtra(PlaybackSessionService.EXTRA_SEEK_FRACTION, fraction.coerceIn(0f, 1f))
        }

    fun selectQueueTrack(trackId: String) =
        sendForActiveQueue(PlaybackSessionService.ACTION_SELECT_TRACK) {
            putExtra(PlaybackSessionService.EXTRA_TRACK_ID, trackId)
        }

    fun updateTrack(trackId: String) =
        sendForActiveQueue(PlaybackSessionService.ACTION_UPDATE_TRACK) {
            putExtra(PlaybackSessionService.EXTRA_TRACK_ID, trackId)
        }

    fun updateQueueTitle(previousTitle: String, newTitle: String) =
        sendForActiveQueue(PlaybackSessionService.ACTION_UPDATE_QUEUE_TITLE) {
            putExtra(PlaybackSessionService.EXTRA_PREVIOUS_QUEUE_TITLE, previousTitle)
            putExtra(PlaybackSessionService.EXTRA_QUEUE_TITLE, newTitle)
        }

    fun clearActivePlaylist(playlistId: String) =
        sendForActiveQueue(PlaybackSessionService.ACTION_CLEAR_ACTIVE_PLAYLIST) {
            putExtra(PlaybackSessionService.EXTRA_PLAYLIST_ID, playlistId)
        }

    fun removeTrack(trackId: String) =
        sendForActiveQueue(PlaybackSessionService.ACTION_REMOVE_TRACK) {
            putExtra(PlaybackSessionService.EXTRA_TRACK_ID, trackId)
        }

    internal fun publish(state: PlaybackState) {
        mutableState.value = state
    }

    private fun send(
        action: String,
        extras: Intent.() -> Unit = {},
    ) {
        val intent = Intent(appContext, PlaybackSessionService::class.java)
            .setAction(action)
            .apply(extras)
        messages.attempt("Не удалось запустить плеер. Откройте приложение и повторите действие.") {
            // These commands originate in the visible UI. A restored queue may
            // be paused, empty or contain missing files, so it cannot promise to
            // enter foreground playback before Android's five-second deadline.
            // MediaSessionService promotes itself when playback actually starts.
            appContext.startService(intent)
        }
    }

    private fun sendForActiveQueue(
        action: String,
        extras: Intent.() -> Unit = {},
    ) {
        if (mutableState.value.currentTrackId == null) return
        send(action = action, extras = extras)
    }
}
