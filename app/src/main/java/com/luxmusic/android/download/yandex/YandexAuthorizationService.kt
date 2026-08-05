package com.luxmusic.android.download.yandex

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.luxmusic.android.LuxMusicApp
import com.luxmusic.android.MainActivity
import com.luxmusic.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the short Yandex device authorization window alive while the browser is foreground. */
class YandexAuthorizationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    private val luxApp: LuxMusicApp
        get() = application as LuxMusicApp

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Авторизация Яндекс Музыки",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ожидание подтверждения входа в Яндекс Музыку"
                setShowBadge(false)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            luxApp.linkDownloader.cancelYandexAuthorization()
            finish(startId)
            return START_NOT_STICKY
        }
        if (!luxApp.linkDownloader.hasPendingYandexAuthorization()) {
            finish(startId)
            return START_NOT_STICKY
        }

        startAsForeground()
        if (pollingJob?.isActive != true) {
            pollingJob = serviceScope.launch {
                luxApp.linkDownloader.completeYandexAuthorization()
                finish(startId)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val state = luxApp.linkDownloader.yandexAuthState.value
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, YandexAuthorizationService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Подтвердите вход в Яндекс Музыку")
            .setContentText(state.userCode?.let { "Код: $it" } ?: "Ожидаем подтверждение в браузере")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Отменить", cancelIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun finish(startId: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelfResult(startId)
    }

    companion object {
        private const val CHANNEL_ID = "luxmusic_yandex_authorization"
        private const val NOTIFICATION_ID = 6_101
        private const val ACTION_START = "com.luxmusic.android.action.START_YANDEX_AUTH"
        private const val ACTION_CANCEL = "com.luxmusic.android.action.CANCEL_YANDEX_AUTH"

        fun start(context: Context): Result<Unit> = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, YandexAuthorizationService::class.java).setAction(ACTION_START),
            )
        }.map { Unit }

        fun stop(context: Context) {
            context.stopService(Intent(context, YandexAuthorizationService::class.java))
        }
    }
}
