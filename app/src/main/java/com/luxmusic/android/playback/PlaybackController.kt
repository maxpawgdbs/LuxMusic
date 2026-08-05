package com.luxmusic.android.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.luxmusic.android.MainActivity
import com.luxmusic.android.data.LibraryStore
import com.luxmusic.android.data.PlaybackState
import com.luxmusic.android.data.RepeatMode
import com.luxmusic.android.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@UnstableApi
internal class PlaybackController(
    private val service: PlaybackSessionService,
    private val libraryStore: LibraryStore,
    private val stateSink: (PlaybackState) -> Unit,
) {
    private val appContext = service.applicationContext
    private val playbackPreferences = appContext.getSharedPreferences(PLAYBACK_PREFERENCES, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = ExoPlayer.Builder(service)
        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        .build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
        }
    private var currentQueue: List<Track> = emptyList()
    private var currentQueueTitle: String = DEFAULT_QUEUE_TITLE
    private var currentPlaylistId: String? = null
    private var lastPersistedState: PersistedPlaybackState? = null

    private val mediaSession = MediaSession.Builder(service, player)
        .setId("luxmusic_media_session")
        .setPeriodicPositionUpdateEnabled(true)
        .build()

    init {
        Log.i(TAG, "Created the sole Player and MediaSession for ${service::class.java.simpleName}")
        mediaSession.setSessionActivity(contentIntent())

        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    publishState()
                }
            },
        )

        restorePlaybackState()

        scope.launch {
            while (isActive) {
                publishState()
                delay(500)
            }
        }
    }

    fun playCollection(
        tracks: List<Track>,
        startIndex: Int,
        queueTitle: String,
        playlistId: String? = null,
    ) {
        playOrToggleCollection(tracks, startIndex, queueTitle, playlistId)
    }

    fun playOrToggleCollection(
        tracks: List<Track>,
        startIndex: Int,
        queueTitle: String,
        playlistId: String? = null,
    ) {
        if (tracks.isEmpty() || startIndex !in tracks.indices) return

        val sameQueue = currentQueue.map(Track::id) == tracks.map(Track::id)
        val selectedTrack = tracks[startIndex]
        val currentTrackId = player.currentMediaItem?.mediaId

        currentQueue = tracks
        currentQueueTitle = queueTitle
        currentPlaylistId = playlistId

        if (sameQueue && currentTrackId == selectedTrack.id) {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(startIndex, 0L)
            }
            player.play()
            publishState()
            return
        }

        if (sameQueue && player.mediaItemCount == tracks.size) {
            player.seekTo(startIndex, 0L)
            player.play()
            publishState()
            return
        }

        val mediaItems = tracks.map(::mediaItem)

        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
        publishState()
    }

    fun togglePlayback() {
        if (player.mediaItemCount == 0) return

        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        publishState()
    }

    fun skipNext() {
        if (player.mediaItemCount == 0) return

        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (player.mediaItemCount > 0) {
            player.seekTo(0, 0L)
        }
        player.play()
        publishState()
    }

    fun skipPrevious() {
        if (player.mediaItemCount == 0) return

        if (player.currentPosition > 3_000) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0, 0L)
        }
        player.play()
        publishState()
    }

    fun seekBack() {
        if (player.mediaItemCount == 0) return

        player.seekBack()
        publishState()
    }

    fun seekForward() {
        if (player.mediaItemCount == 0) return

        player.seekForward()
        publishState()
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        if (player.shuffleModeEnabled) {
            player.repeatMode = Player.REPEAT_MODE_ALL
        }
        publishState()
    }

    fun cycleRepeatMode() {
        player.repeatMode = if (player.shuffleModeEnabled) {
            Player.REPEAT_MODE_ALL
        } else {
            when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
        publishState()
    }

    fun seekToFraction(fraction: Float) {
        val duration = player.duration.takeIf { it > 0L } ?: return
        player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        publishState()
    }

    fun selectQueueTrack(trackId: String) {
        val index = currentQueue.indexOfFirst { it.id == trackId }
        if (index < 0 || index >= player.mediaItemCount) return

        player.seekTo(index, 0L)
        player.play()
        publishState()
    }

    fun updateTrackDetails(updatedTrack: Track) {
        val index = currentQueue.indexOfFirst { it.id == updatedTrack.id }
        if (index < 0 || index >= player.mediaItemCount) return

        currentQueue = currentQueue.toMutableList().also { queue ->
            queue[index] = updatedTrack
        }
        val currentItem = player.getMediaItemAt(index)
        player.replaceMediaItem(
            index,
            currentItem.buildUpon()
                .setMediaMetadata(
                    currentItem.mediaMetadata.buildUpon()
                        .setTitle(updatedTrack.title)
                        .setArtist(updatedTrack.artist)
                        .setAlbumTitle(updatedTrack.album)
                        .setArtworkUri(updatedTrack.artworkPath?.let(::File)?.toUri())
                        .build(),
                )
                .build(),
        )
        publishState()
    }

    fun updateQueueTitle(previousTitle: String, newTitle: String) {
        if (currentQueueTitle != previousTitle) return
        currentQueueTitle = newTitle
        publishState()
    }

    fun clearActivePlaylist(playlistId: String) {
        if (currentPlaylistId != playlistId) return
        currentPlaylistId = null
        publishState()
    }

    fun stopPlayback() {
        player.stop()
        player.clearMediaItems()
        currentQueue = emptyList()
        currentQueueTitle = DEFAULT_QUEUE_TITLE
        currentPlaylistId = null
        playbackPreferences.edit().clear().apply()
        lastPersistedState = null
        publishState()
    }

    fun removeTrack(trackId: String) {
        val index = currentQueue.indexOfFirst { it.id == trackId }
        currentQueue = currentQueue.filterNot { it.id == trackId }

        if (index >= 0 && index < player.mediaItemCount) {
            player.removeMediaItem(index)
        }

        if (currentQueue.isEmpty()) {
            currentQueueTitle = DEFAULT_QUEUE_TITLE
            currentPlaylistId = null
            player.stop()
            player.clearMediaItems()
        }

        publishState()
    }

    fun mediaSession(): MediaSession = mediaSession

    fun hasMediaItems(): Boolean = player.mediaItemCount > 0

    fun shouldRemainWhenTaskRemoved(): Boolean = PlaybackTaskPolicy.shouldKeepService(
        hasMediaItems = player.mediaItemCount > 0,
        playWhenReady = player.playWhenReady,
        playbackEnded = player.playbackState == Player.STATE_ENDED,
    )

    fun release() {
        Log.i(TAG, "Releasing Player and MediaSession")
        scope.cancel()
        mediaSession.release()
        player.release()
    }

    private fun publishState() {
        val playbackState = PlaybackState(
            currentTrackId = player.currentMediaItem?.mediaId,
            queueTrackIds = visibleQueueIds(),
            queueTitle = currentQueueTitle,
            activePlaylistId = currentPlaylistId,
            isPlaying = player.isPlaying,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.NONE
            },
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
        )
        stateSink(playbackState)
        persistPlaybackState()
    }

    private fun visibleQueueIds(): List<String> {
        if (player.mediaItemCount == 0) return emptyList()

        var index = player.currentMediaItemIndex
        if (index == C.INDEX_UNSET) return currentQueue.map(Track::id)

        val visited = mutableSetOf<Int>()
        val orderedIds = mutableListOf<String>()
        while (index != C.INDEX_UNSET && visited.add(index)) {
            orderedIds += player.getMediaItemAt(index).mediaId
            index = player.getNextMediaItemIndex()
        }
        return orderedIds
    }

    private fun mediaItem(track: Track): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(File(track.localPath).toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.artworkPath?.let(::File)?.toUri())
                    .build(),
            )
            .build()
    }

    private fun restorePlaybackState() {
        val savedIds = playbackPreferences.getString(KEY_QUEUE_IDS, null)
            ?.split(',')
            ?.filter(String::isNotBlank)
            .orEmpty()
        if (savedIds.isEmpty()) return

        val tracksById = libraryStore.snapshot.value.tracks.associateBy(Track::id)
        val restoredQueue = savedIds
            .mapNotNull(tracksById::get)
            .filter { File(it.localPath).exists() }
        if (restoredQueue.isEmpty()) {
            playbackPreferences.edit().clear().apply()
            return
        }

        currentQueue = restoredQueue
        currentQueueTitle = playbackPreferences.getString(KEY_QUEUE_TITLE, DEFAULT_QUEUE_TITLE)
            .orEmpty()
            .ifBlank { DEFAULT_QUEUE_TITLE }
        currentPlaylistId = playbackPreferences.getString(KEY_PLAYLIST_ID, null)
        val savedTrackId = playbackPreferences.getString(KEY_CURRENT_TRACK_ID, null)
        val startIndex = restoredQueue.indexOfFirst { it.id == savedTrackId }.coerceAtLeast(0)
        val positionMs = playbackPreferences.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L)

        player.setMediaItems(restoredQueue.map(::mediaItem), startIndex, positionMs)
        player.shuffleModeEnabled = playbackPreferences.getBoolean(KEY_SHUFFLE, false)
        player.repeatMode = playbackPreferences.getInt(KEY_REPEAT_MODE, Player.REPEAT_MODE_OFF)
        if (player.shuffleModeEnabled && player.repeatMode == Player.REPEAT_MODE_OFF) {
            player.repeatMode = Player.REPEAT_MODE_ALL
        }
        player.prepare()
        if (playbackPreferences.getBoolean(KEY_PLAY_WHEN_READY, false)) {
            player.play()
        }
        publishState()
    }

    private fun persistPlaybackState() {
        if (currentQueue.isEmpty() || player.mediaItemCount == 0) {
            if (lastPersistedState != null || playbackPreferences.contains(KEY_QUEUE_IDS)) {
                playbackPreferences.edit().clear().apply()
                lastPersistedState = null
            }
            return
        }

        val persistedState = PersistedPlaybackState(
            queueIds = currentQueue.map(Track::id),
            queueTitle = currentQueueTitle,
            playlistId = currentPlaylistId,
            currentTrackId = player.currentMediaItem?.mediaId,
            positionBucket = player.currentPosition.coerceAtLeast(0L) / PERSIST_POSITION_INTERVAL_MS,
            playWhenReady = player.playWhenReady,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
        if (persistedState == lastPersistedState) return

        playbackPreferences.edit()
            .putString(KEY_QUEUE_IDS, persistedState.queueIds.joinToString(","))
            .putString(KEY_QUEUE_TITLE, persistedState.queueTitle)
            .putString(KEY_PLAYLIST_ID, persistedState.playlistId)
            .putString(KEY_CURRENT_TRACK_ID, persistedState.currentTrackId)
            .putLong(KEY_POSITION_MS, player.currentPosition.coerceAtLeast(0L))
            .putBoolean(KEY_PLAY_WHEN_READY, persistedState.playWhenReady)
            .putBoolean(KEY_SHUFFLE, persistedState.shuffleEnabled)
            .putInt(KEY_REPEAT_MODE, persistedState.repeatMode)
            .apply()
        lastPersistedState = persistedState
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private data class PersistedPlaybackState(
        val queueIds: List<String>,
        val queueTitle: String,
        val playlistId: String?,
        val currentTrackId: String?,
        val positionBucket: Long,
        val playWhenReady: Boolean,
        val shuffleEnabled: Boolean,
        val repeatMode: Int,
    )

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
        const val DEFAULT_QUEUE_TITLE = "Библиотека"
        const val PERSIST_POSITION_INTERVAL_MS = 5_000L
        const val PLAYBACK_PREFERENCES = "luxmusic_playback_state"
        const val KEY_QUEUE_IDS = "queue_ids"
        const val KEY_QUEUE_TITLE = "queue_title"
        const val KEY_PLAYLIST_ID = "playlist_id"
        const val KEY_CURRENT_TRACK_ID = "current_track_id"
        const val KEY_POSITION_MS = "position_ms"
        const val KEY_PLAY_WHEN_READY = "play_when_ready"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT_MODE = "repeat_mode"
        const val TAG = "LuxPlayback"
    }
}

internal object PlaybackTaskPolicy {
    fun shouldKeepService(
        hasMediaItems: Boolean,
        playWhenReady: Boolean,
        playbackEnded: Boolean,
    ): Boolean {
        return hasMediaItems && playWhenReady && !playbackEnded
    }
}
