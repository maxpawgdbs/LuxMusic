package com.luxmusic.android.playback

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

class PlaybackNotificationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action != ACTION_START_OR_UPDATE) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = intent.notificationExtra() ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (notificationId == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(notificationId, notification)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        detachForegroundNotification()
        super.onDestroy()
    }

    private fun detachForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
    }

    private fun Intent.notificationExtra(): Notification? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_NOTIFICATION, Notification::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_NOTIFICATION)
        }
    }

    companion object {
        private const val ACTION_START_OR_UPDATE = "com.luxmusic.android.playback.START_OR_UPDATE_NOTIFICATION"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val EXTRA_NOTIFICATION = "notification"

        fun startOrUpdate(
            context: Context,
            notificationId: Int,
            notification: Notification,
        ) {
            val intent = Intent(context, PlaybackNotificationService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_NOTIFICATION, notification)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackNotificationService::class.java))
        }
    }
}
