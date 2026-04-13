package com.luxmusic.android.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.luxmusic.android.MainActivity
import com.luxmusic.android.R
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

@UnstableApi
class PlaybackController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = ExoPlayer.Builder(appContext).build().apply {
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
    private var currentQueueTitle: String = "Библиотека"

    private val mediaSession = MediaSession.Builder(appContext, player)
        .setId("luxmusic_media_session")
        .build()

    private val notificationManager = PlayerNotificationManager.Builder(
        appContext,
        PLAYBACK_NOTIFICATION_ID,
        PLAYBACK_CHANNEL_ID,
    )
        .setChannelNameResourceId(R.string.playback_notification_channel_name)
        .setChannelDescriptionResourceId(R.string.playback_notification_channel_description)
        .setMediaDescriptionAdapter(NotificationDescriptionAdapter())
        .setCustomActionReceiver(RepeatActionReceiver())
        .setSmallIconResourceId(android.R.drawable.ic_media_play)
        .build().apply {
            setColorized(true)
            setColor(0xFFF6B91A.toInt())
            setPriority(NotificationCompat.PRIORITY_LOW)
            setUseFastForwardAction(false)
            setUseRewindAction(false)
            setUseStopAction(false)
            setUseNextActionInCompactView(true)
            setUsePreviousActionInCompactView(true)
            setPlayer(player)
        }

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

        currentQueue = tracks
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

    fun removeTrack(trackId: String) {
        val index = currentQueue.indexOfFirst { it.id == trackId }
        currentQueue = currentQueue.filterNot { it.id == trackId }

        if (index >= 0 && index < player.mediaItemCount) {
            player.removeMediaItem(index)
        }

        if (currentQueue.isEmpty()) {
            currentQueueTitle = "Библиотека"
            player.stop()
            player.clearMediaItems()
        }

        publishState()
    }

    fun release() {
        scope.cancel()
        notificationManager.setPlayer(null)
        mediaSession.release()
        player.release()
    }

    private fun publishState() {
        notificationManager.invalidate()
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
    }

    private fun currentTrack(): Track? {
        val currentId = player.currentMediaItem?.mediaId ?: return null
        return currentQueue.firstOrNull { it.id == currentId }
    }

    private fun playbackModeSummary(): String {
        val repeatLabel = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> "Повтор трека"
            Player.REPEAT_MODE_ALL -> "Повтор очереди"
            else -> "Без повтора"
        }
        val shuffleLabel = if (player.shuffleModeEnabled) "Shuffle" else "По порядку"
        return "$repeatLabel • $shuffleLabel"
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

    private inner class NotificationDescriptionAdapter : PlayerNotificationManager.MediaDescriptionAdapter {
        override fun createCurrentContentIntent(player: Player): PendingIntent = contentIntent()

        override fun getCurrentContentTitle(player: Player): CharSequence {
            return currentTrack()?.title ?: "LuxMusic"
        }

        override fun getCurrentContentText(player: Player): CharSequence {
            val track = currentTrack()
            return if (track != null) {
                "${track.artist} • ${track.album}"
            } else {
                "Локальная музыкальная библиотека"
            }
        }

        override fun getCurrentSubText(player: Player): CharSequence {
            return playbackModeSummary()
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback,
        ): Bitmap? {
            val artworkPath = currentTrack()?.artworkPath ?: return null
            return runCatching { BitmapFactory.decodeFile(artworkPath) }.getOrNull()
        }
    }

    private inner class RepeatActionReceiver : PlayerNotificationManager.CustomActionReceiver {
        override fun createCustomActions(
            context: Context,
            instanceId: Int,
        ): Map<String, NotificationCompat.Action> {
            val intent = Intent(ACTION_REPEAT_MODE)
                .setPackage(context.packageName)
                .putExtra(PlayerNotificationManager.EXTRA_INSTANCE_ID, instanceId)

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                instanceId + 41,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            return mapOf(
                ACTION_REPEAT_MODE to NotificationCompat.Action(
                    android.R.drawable.ic_menu_rotate,
                    context.getString(R.string.notification_repeat_mode),
                    pendingIntent,
                ),
            )
        }

        override fun getCustomActions(player: Player): List<String> {
            return if (player.mediaItemCount > 0) listOf(ACTION_REPEAT_MODE) else emptyList()
        }

        override fun onCustomAction(player: Player, action: String, intent: Intent) {
            if (action == ACTION_REPEAT_MODE) {
                cycleRepeatMode()
            }
        }
    }

    private companion object {
        const val PLAYBACK_NOTIFICATION_ID = 1207
        const val PLAYBACK_CHANNEL_ID = "luxmusic_playback"
        const val ACTION_REPEAT_MODE = "com.luxmusic.android.action.REPEAT_MODE"
    }
}
