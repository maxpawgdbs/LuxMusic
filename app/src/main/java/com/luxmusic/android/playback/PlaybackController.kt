package com.luxmusic.android.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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

class PlaybackController(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = ExoPlayer.Builder(context).build()
    private val mutableState = MutableStateFlow(PlaybackState())

    private var currentQueueIds: List<String> = emptyList()
    private var currentQueueTitle: String = "Библиотека"

    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    init {
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
        if (tracks.isEmpty()) return

        currentQueueIds = tracks.map(Track::id)
        currentQueueTitle = queueTitle

        val mediaItems = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(File(track.localPath).toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
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
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        publishState()
    }

    fun skipNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (player.mediaItemCount > 0) {
            player.seekTo(0, 0L)
        }
        player.play()
        publishState()
    }

    fun skipPrevious() {
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

    fun release() {
        scope.cancel()
        player.release()
    }

    private fun publishState() {
        mutableState.value = PlaybackState(
            currentTrackId = player.currentMediaItem?.mediaId,
            queueTrackIds = currentQueueIds,
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
    }
}
