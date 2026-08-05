package com.luxmusic.android.playback

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.luxmusic.android.R
import java.util.Locale

/**
 * Keeps Media3's real MediaStyle notification and session token, then gives the drawer views a
 * compact LuxMusic presentation. Android's system media surface owns the draggable seek bar;
 * ACTION_SEEK_TO remains available through the same session and player.
 */
@UnstableApi
internal class LuxMediaNotificationProvider(
    context: Context,
    private val queueTitle: () -> String,
) : MediaNotification.Provider {
    private val appContext = context.applicationContext
    private val delegate = DefaultMediaNotificationProvider.Builder(appContext)
        .setChannelId(CHANNEL_ID)
        .setChannelName(R.string.playback_notification_channel)
        .build()
        .apply { setSmallIcon(R.drawable.ic_notification) }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val decoratingCallback = object : MediaNotification.Provider.Callback {
            override fun onNotificationChanged(mediaNotification: MediaNotification) {
                onNotificationChangedCallback.onNotificationChanged(
                    decorate(mediaNotification, mediaSession, actionFactory),
                )
            }
        }
        return delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            decoratingCallback,
        ).let { decorate(it, mediaSession, actionFactory) }
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: android.os.Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
        return delegate.notificationChannelInfo
    }

    @Suppress("DEPRECATION")
    private fun decorate(
        source: MediaNotification,
        session: MediaSession,
        actionFactory: MediaNotification.ActionFactory,
    ): MediaNotification {
        val player = session.player
        val metadata = player.mediaMetadata
        val title = metadata.title?.toString().orEmpty().ifBlank {
            appContext.getString(R.string.app_name)
        }
        val artist = metadata.artist?.toString().orEmpty().ifBlank {
            appContext.getString(R.string.notification_unknown_artist)
        }
        val durationMs = player.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?: metadata.durationMs?.takeIf { it > 0L }
            ?: 0L
        val positionMs = player.currentPosition.coerceIn(0L, durationMs.coerceAtLeast(0L))
        val snapshot = NotificationSnapshot(
            title = title,
            artist = artist,
            queueTitle = queueTitle().ifBlank { appContext.getString(R.string.library_queue) },
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = player.isPlaying,
        )
        val notification = source.notification
        notification.contentView = notificationViews(
            layoutId = R.layout.playback_notification_compact,
            snapshot = snapshot,
            notification = notification,
            session = session,
            actionFactory = actionFactory,
        )
        notification.bigContentView = notificationViews(
            layoutId = R.layout.playback_notification_expanded,
            snapshot = snapshot,
            notification = notification,
            session = session,
            actionFactory = actionFactory,
        )
        return MediaNotification(source.notificationId, notification)
    }

    private fun notificationViews(
        layoutId: Int,
        snapshot: NotificationSnapshot,
        notification: android.app.Notification,
        session: MediaSession,
        actionFactory: MediaNotification.ActionFactory,
    ): RemoteViews {
        val maxProgress = snapshot.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val progress = snapshot.positionMs.coerceAtMost(maxProgress.toLong()).toInt()
        return RemoteViews(appContext.packageName, layoutId).apply {
            setOnClickPendingIntent(R.id.notification_root, session.sessionActivity)
            setOnClickPendingIntent(
                R.id.notification_previous,
                actionFactory.createMediaActionPendingIntent(session, Player.COMMAND_SEEK_TO_PREVIOUS),
            )
            setOnClickPendingIntent(
                R.id.notification_toggle,
                actionFactory.createMediaActionPendingIntent(session, Player.COMMAND_PLAY_PAUSE),
            )
            setOnClickPendingIntent(
                R.id.notification_next,
                actionFactory.createMediaActionPendingIntent(session, Player.COMMAND_SEEK_TO_NEXT),
            )
            setImageViewResource(
                R.id.notification_toggle,
                if (snapshot.isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
            )
            setContentDescription(
                R.id.notification_toggle,
                appContext.getString(
                    if (snapshot.isPlaying) R.string.notification_pause
                    else R.string.notification_play,
                ),
            )
            setTextViewText(R.id.notification_title, snapshot.title)
            setTextViewText(R.id.notification_details, snapshot.artist)
            setTextViewText(R.id.notification_queue, snapshot.queueTitle)
            setChronometer(
                R.id.notification_position,
                SystemClock.elapsedRealtime() - snapshot.positionMs,
                null,
                snapshot.isPlaying,
            )
            setTextViewText(R.id.notification_duration, formatTime(snapshot.durationMs))
            setProgressBar(
                R.id.notification_progress,
                maxProgress.coerceAtLeast(1),
                progress,
                maxProgress <= 0,
            )
            notification.getLargeIcon()?.let { setImageViewIcon(R.id.notification_artwork, it) }
                ?: setImageViewResource(R.id.notification_artwork, R.drawable.icon)

            if (layoutId == R.layout.playback_notification_expanded) {
                setOnClickPendingIntent(
                    R.id.notification_seek_back,
                    actionFactory.createMediaActionPendingIntent(session, Player.COMMAND_SEEK_BACK),
                )
                setOnClickPendingIntent(
                    R.id.notification_seek_forward,
                    actionFactory.createMediaActionPendingIntent(session, Player.COMMAND_SEEK_FORWARD),
                )
            }
        }
    }

    private data class NotificationSnapshot(
        val title: String,
        val artist: String,
        val queueTitle: String,
        val positionMs: Long,
        val durationMs: Long,
        val isPlaying: Boolean,
    )

    companion object {
        internal const val CHANNEL_ID = "luxmusic_playback_v053"

        private fun formatTime(valueMs: Long): String {
            val totalSeconds = valueMs.coerceAtLeast(0L) / 1_000L
            val hours = totalSeconds / 3_600L
            val minutes = (totalSeconds % 3_600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
            }
        }
    }
}
