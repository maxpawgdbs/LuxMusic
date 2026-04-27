package com.luxmusic.android.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.luxmusic.android.LuxMusicApp
import com.luxmusic.android.MainActivity
import com.luxmusic.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class PlaybackNotificationService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private var refreshJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS)
        addSession(playbackController().notificationMediaSession())
        ensureRefreshLoop()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return playbackController().notificationMediaSession()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val controller = playbackController()
        when (intent?.action ?: ACTION_SYNC) {
            ACTION_TOGGLE_PLAYBACK -> controller.togglePlayback()
            ACTION_SKIP_PREVIOUS -> controller.skipPrevious()
            ACTION_SKIP_NEXT -> controller.skipNext()
            ACTION_SEEK_BACK -> controller.seekBack()
            ACTION_SEEK_FORWARD -> controller.seekForward()
            ACTION_SYNC -> Unit
            else -> Unit
        }

        val result = super.onStartCommand(intent, flags, startId)
        ensureRefreshLoop()
        triggerNotificationUpdate()
        return result
    }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        val controller = playbackController()
        val snapshot = controller.notificationSnapshot()
        if (snapshot == null) {
            notificationManager.cancel(NOTIFICATION_ID)
            stopForegroundCompat(removeNotification = true)
            if (!isPlaybackOngoing()) {
                stopSelf()
            }
            return
        }

        val notification = buildNotification(snapshot, controller)
        if (startInForegroundRequired || snapshot.isPlaying) {
            startForegroundCompat(notification)
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        removeSession(playbackController().notificationMediaSession())
        notificationManager.cancel(NOTIFICATION_ID)
        stopForegroundCompat(removeNotification = true)
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureRefreshLoop() {
        if (refreshJob?.isActive == true) return

        refreshJob = scope.launch {
            while (isActive) {
                val snapshot = playbackController().notificationSnapshot()
                if (snapshot == null) {
                    triggerNotificationUpdate()
                    if (!isPlaybackOngoing()) {
                        stopSelf()
                        break
                    }
                    delay(IDLE_REFRESH_MS)
                    continue
                }
                triggerNotificationUpdate()
                delay(if (snapshot.isPlaying) ACTIVE_REFRESH_MS else IDLE_REFRESH_MS)
            }
        }
    }

    private fun buildNotification(
        snapshot: PlaybackNotificationSnapshot,
        controller: PlaybackController,
    ): Notification {
        val maxProgress = snapshot.durationMs
            .coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val progress = snapshot.positionMs
            .coerceAtLeast(0L)
            .coerceAtMost(maxProgress.toLong())
            .toInt()
        val isIndeterminate = maxProgress <= 0
        val mediaStyle = MediaStyleNotificationHelper.DecoratedMediaCustomViewStyle(controller.notificationMediaSession())
            .setShowActionsInCompactView(0, 2, 4)
        val compactRemoteViews = compactRemoteViews(snapshot, progress, maxProgress, isIndeterminate)
        val expandedRemoteViews = expandedRemoteViews(snapshot, progress, maxProgress, isIndeterminate)

        return NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(snapshot.title)
            .setContentText(snapshot.contentText)
            .setSubText(snapshot.subText)
            .setContentIntent(mainActivityPendingIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setColorized(true)
            .setColor(0xFF215EEA.toInt())
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setLargeIcon(snapshot.artwork)
            .setProgress(maxProgress, progress, isIndeterminate)
            .setCustomContentView(compactRemoteViews)
            .setCustomBigContentView(expandedRemoteViews)
            .setCustomHeadsUpContentView(expandedRemoteViews)
            .addAction(action(ACTION_SKIP_PREVIOUS, REQUEST_PREVIOUS, android.R.drawable.ic_media_previous, R.string.notification_previous))
            .addAction(action(ACTION_SEEK_BACK, REQUEST_SEEK_BACK, android.R.drawable.ic_media_rew, R.string.notification_seek_back))
            .addAction(
                action(
                    ACTION_TOGGLE_PLAYBACK,
                    REQUEST_TOGGLE_PLAYBACK,
                    if (snapshot.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (snapshot.isPlaying) R.string.notification_pause else R.string.notification_play,
                ),
            )
            .addAction(action(ACTION_SEEK_FORWARD, REQUEST_SEEK_FORWARD, android.R.drawable.ic_media_ff, R.string.notification_seek_forward))
            .addAction(action(ACTION_SKIP_NEXT, REQUEST_NEXT, android.R.drawable.ic_media_next, R.string.notification_next))
            .setStyle(mediaStyle)
            .setPublicVersion(publicNotification(snapshot, controller, progress, maxProgress, isIndeterminate))
            .build()
    }

    private fun compactRemoteViews(
        snapshot: PlaybackNotificationSnapshot,
        progress: Int,
        maxProgress: Int,
        isIndeterminate: Boolean,
    ): RemoteViews {
        return createRemoteViews(
            layoutId = R.layout.playback_notification_compact,
            snapshot = snapshot,
            progress = progress,
            maxProgress = maxProgress,
            isIndeterminate = isIndeterminate,
        )
    }

    private fun expandedRemoteViews(
        snapshot: PlaybackNotificationSnapshot,
        progress: Int,
        maxProgress: Int,
        isIndeterminate: Boolean,
    ): RemoteViews {
        return createRemoteViews(
            layoutId = R.layout.playback_notification_expanded,
            snapshot = snapshot,
            progress = progress,
            maxProgress = maxProgress,
            isIndeterminate = isIndeterminate,
        )
    }

    private fun createRemoteViews(
        layoutId: Int,
        snapshot: PlaybackNotificationSnapshot,
        progress: Int,
        maxProgress: Int,
        isIndeterminate: Boolean,
    ): RemoteViews {
        return RemoteViews(packageName, layoutId).apply {
            setOnClickPendingIntent(R.id.notification_root, mainActivityPendingIntent())
            setTextViewText(R.id.notification_title, snapshot.title)
            setTextViewText(R.id.notification_details, snapshot.contentText)
            setTextViewText(R.id.notification_queue, snapshot.subText)
            setTextViewText(R.id.notification_position, formatTime(snapshot.positionMs))
            setTextViewText(R.id.notification_duration, formatTime(snapshot.durationMs))
            setProgressBar(R.id.notification_progress, maxProgress.coerceAtLeast(1), progress, isIndeterminate)
            if (snapshot.artwork != null) {
                setImageViewBitmap(R.id.notification_artwork, snapshot.artwork)
            } else {
                setImageViewResource(R.id.notification_artwork, android.R.drawable.ic_media_play)
            }
        }
    }

    private fun publicNotification(
        snapshot: PlaybackNotificationSnapshot,
        controller: PlaybackController,
        progress: Int,
        maxProgress: Int,
        isIndeterminate: Boolean,
    ): Notification {
        val compactRemoteViews = compactRemoteViews(snapshot, progress, maxProgress, isIndeterminate)
        val expandedRemoteViews = expandedRemoteViews(snapshot, progress, maxProgress, isIndeterminate)
        return NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(snapshot.title)
            .setContentText(snapshot.contentText)
            .setSubText(snapshot.subText)
            .setContentIntent(mainActivityPendingIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setColorized(true)
            .setColor(0xFF215EEA.toInt())
            .setLargeIcon(snapshot.artwork)
            .setProgress(maxProgress, progress, isIndeterminate)
            .setCustomContentView(compactRemoteViews)
            .setCustomBigContentView(expandedRemoteViews)
            .setCustomHeadsUpContentView(expandedRemoteViews)
            .setStyle(
                MediaStyleNotificationHelper.DecoratedMediaCustomViewStyle(controller.notificationMediaSession())
                    .setShowActionsInCompactView(0, 2, 4),
            )
            .addAction(action(ACTION_SKIP_PREVIOUS, REQUEST_PREVIOUS, android.R.drawable.ic_media_previous, R.string.notification_previous))
            .addAction(action(ACTION_SEEK_BACK, REQUEST_SEEK_BACK, android.R.drawable.ic_media_rew, R.string.notification_seek_back))
            .addAction(
                action(
                    ACTION_TOGGLE_PLAYBACK,
                    REQUEST_TOGGLE_PLAYBACK,
                    if (snapshot.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (snapshot.isPlaying) R.string.notification_pause else R.string.notification_play,
                ),
            )
            .addAction(action(ACTION_SEEK_FORWARD, REQUEST_SEEK_FORWARD, android.R.drawable.ic_media_ff, R.string.notification_seek_forward))
            .addAction(action(ACTION_SKIP_NEXT, REQUEST_NEXT, android.R.drawable.ic_media_next, R.string.notification_next))
            .build()
    }

    private fun action(
        action: String,
        requestCode: Int,
        iconRes: Int,
        titleRes: Int,
    ): NotificationCompat.Action {
        return NotificationCompat.Action(
            iconRes,
            getString(titleRes),
            servicePendingIntent(action, requestCode),
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlaybackNotificationService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun mainActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun playbackController(): PlaybackController {
        return (application as LuxMusicApp).playbackController
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_PLAYBACK_CHANNEL_ID)
        if (manager.getNotificationChannel(PLAYBACK_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            getString(R.string.playback_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.playback_notification_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(
                if (removeNotification) {
                    STOP_FOREGROUND_REMOVE
                } else {
                    STOP_FOREGROUND_DETACH
                },
            )
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
    }

    companion object {
        private const val LEGACY_PLAYBACK_CHANNEL_ID = "luxmusic_playback"
        private const val PLAYBACK_CHANNEL_ID = "luxmusic_playback_v3"
        private const val NOTIFICATION_ID = 1207
        private const val ACTION_SYNC = "com.luxmusic.android.playback.SYNC"
        private const val ACTION_TOGGLE_PLAYBACK = "com.luxmusic.android.playback.TOGGLE"
        private const val ACTION_SKIP_PREVIOUS = "com.luxmusic.android.playback.PREVIOUS"
        private const val ACTION_SKIP_NEXT = "com.luxmusic.android.playback.NEXT"
        private const val ACTION_SEEK_BACK = "com.luxmusic.android.playback.SEEK_BACK"
        private const val ACTION_SEEK_FORWARD = "com.luxmusic.android.playback.SEEK_FORWARD"
        private const val REQUEST_OPEN_APP = 1
        private const val REQUEST_PREVIOUS = 2
        private const val REQUEST_SEEK_BACK = 3
        private const val REQUEST_TOGGLE_PLAYBACK = 4
        private const val REQUEST_SEEK_FORWARD = 5
        private const val REQUEST_NEXT = 6
        private const val ACTIVE_REFRESH_MS = 1_000L
        private const val IDLE_REFRESH_MS = 2_000L

        private fun formatTime(valueMs: Long): String {
            val totalSeconds = (valueMs.coerceAtLeast(0L) / 1_000L).toInt()
            val hours = totalSeconds / 3_600
            val minutes = (totalSeconds % 3_600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

        fun startOrUpdate(context: Context) {
            val intent = Intent(context, PlaybackNotificationService::class.java).apply {
                action = ACTION_SYNC
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackNotificationService::class.java))
        }
    }
}
