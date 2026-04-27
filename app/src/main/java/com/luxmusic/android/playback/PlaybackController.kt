package com.luxmusic.android.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.luxmusic.android.data.PlaybackState
import com.luxmusic.android.data.RepeatMode
import com.luxmusic.android.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackNotificationSnapshot(
    val title: String,
    val contentText: String,
    val subText: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val artwork: Bitmap?,
)

@UnstableApi
class PlaybackController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = ExoPlayer.Builder(appContext)
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
    private val mutableState = MutableStateFlow(PlaybackState())

    private var currentQueue: List<Track> = emptyList()
    private var currentQueueTitle: String = DEFAULT_QUEUE_TITLE
    private var cachedNotificationArtworkPath: String? = null
    private var cachedNotificationArtwork: Bitmap? = null
    private var playbackForegroundServiceActive = false
    private var lastNotificationSyncState: NotificationSyncState? = null

    private val mediaSession = MediaSession.Builder(appContext, player)
        .setId("luxmusic_media_session")
        .build()

    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    init {
        mediaSession.setSessionActivity(contentIntent())

        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    publishState()
                }
            },
        )

        scope.launch {
            while (isActive) {
                publishState()
                delay(500)
            }
        }
    }

    fun playCollection(tracks: List<Track>, startIndex: Int, queueTitle: String) {
        playOrToggleCollection(tracks, startIndex, queueTitle)
    }

    fun playOrToggleCollection(tracks: List<Track>, startIndex: Int, queueTitle: String) {
        if (tracks.isEmpty() || startIndex !in tracks.indices) return

        val sameQueue = currentQueue.map(Track::id) == tracks.map(Track::id)
        val selectedTrack = tracks[startIndex]
        val currentTrackId = player.currentMediaItem?.mediaId

        if (sameQueue && currentTrackId == selectedTrack.id) {
            togglePlayback()
            return
        }

        currentQueue = tracks
        currentQueueTitle = queueTitle

        if (sameQueue && player.mediaItemCount == tracks.size) {
            player.seekTo(startIndex, 0L)
            player.play()
            publishState()
            return
        }

        val mediaItems = tracks.map { track ->
            MediaItem.Builder()
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
        publishState()
    }

    fun cycleRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publishState()
    }

    fun seekToFraction(fraction: Float) {
        val duration = player.duration.takeIf { it > 0L } ?: return
        player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
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
            player.stop()
            player.clearMediaItems()
        }

        publishState()
    }

    fun notificationSnapshot(): PlaybackNotificationSnapshot? {
        val track = currentTrack() ?: return null
        val durationMs = player.duration
            .takeIf { it > 0L }
            ?: track.durationMs.takeIf { it > 0L }
            ?: 0L
        val safeDurationMs = durationMs.coerceAtLeast(0L)
        val safePositionMs = player.currentPosition
            .coerceAtLeast(0L)
            .coerceAtMost(safeDurationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
        return PlaybackNotificationSnapshot(
            title = track.title.ifBlank { "LuxMusic" },
            contentText = trackDetails(track),
            subText = notificationSubText(),
            isPlaying = player.isPlaying,
            positionMs = safePositionMs,
            durationMs = safeDurationMs,
            artwork = currentArtwork(),
        )
    }

    fun notificationMediaSession(): MediaSession = mediaSession

    fun release() {
        scope.cancel()
        PlaybackNotificationService.stop(appContext)
        playbackForegroundServiceActive = false
        lastNotificationSyncState = null
        mediaSession.release()
        player.release()
    }

    private fun publishState() {
        mutableState.value = PlaybackState(
            currentTrackId = player.currentMediaItem?.mediaId,
            queueTrackIds = currentQueue.map(Track::id),
            queueTitle = currentQueueTitle,
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
        syncPlaybackNotification()
    }

    private fun syncPlaybackNotification() {
        val syncState = notificationSyncState()
        if (syncState == null) {
            if (playbackForegroundServiceActive) {
                PlaybackNotificationService.stop(appContext)
                playbackForegroundServiceActive = false
            }
            lastNotificationSyncState = null
            return
        }

        if (!playbackForegroundServiceActive || syncState != lastNotificationSyncState) {
            PlaybackNotificationService.startOrUpdate(appContext)
            playbackForegroundServiceActive = true
            lastNotificationSyncState = syncState
        }
    }

    private fun notificationSyncState(): NotificationSyncState? {
        val track = currentTrack() ?: return null
        return NotificationSyncState(
            trackId = track.id,
            queueTitle = currentQueueTitle,
            isPlaying = player.isPlaying,
            mediaItemIndex = player.currentMediaItemIndex,
            repeatMode = player.repeatMode,
            shuffleEnabled = player.shuffleModeEnabled,
            artworkPath = track.artworkPath,
        )
    }

    private fun currentTrack(): Track? {
        val currentId = player.currentMediaItem?.mediaId ?: return null
        return currentQueue.firstOrNull { it.id == currentId }
    }

    private fun currentArtwork(): Bitmap? {
        val artworkPath = currentTrack()?.artworkPath ?: run {
            cachedNotificationArtworkPath = null
            cachedNotificationArtwork = null
            return null
        }
        if (cachedNotificationArtworkPath != artworkPath) {
            cachedNotificationArtworkPath = artworkPath
            cachedNotificationArtwork = runCatching { BitmapFactory.decodeFile(artworkPath) }.getOrNull()
        }
        return cachedNotificationArtwork
    }

    private fun trackDetails(track: Track): String {
        val parts = listOf(track.artist, track.album)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        return when {
            parts.isNotEmpty() -> parts.joinToString(" • ")
            currentQueueTitle.isNotBlank() -> currentQueueTitle
            else -> "LuxMusic"
        }
    }

    private fun notificationSubText(): String {
        val mode = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> "Повтор трека"
            Player.REPEAT_MODE_ALL -> "Повтор очереди"
            else -> "Без повтора"
        }
        val shuffle = if (player.shuffleModeEnabled) "Перемешивание" else "По порядку"
        return "$currentQueueTitle • $mode • $shuffle"
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

    private data class NotificationSyncState(
        val trackId: String,
        val queueTitle: String,
        val isPlaying: Boolean,
        val mediaItemIndex: Int,
        val repeatMode: Int,
        val shuffleEnabled: Boolean,
        val artworkPath: String?,
    )

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
        const val DEFAULT_QUEUE_TITLE = "Библиотека"
    }
}
