package com.senda.app.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.senda.app.MainActivity
import com.senda.app.R
import com.senda.app.data.permissions.FocoPermissions
import com.senda.app.data.preferences.ServiceHeartbeatStore
import com.senda.app.domain.lifecycle.HeartbeatHealth

/**
 * Verificador periódico da proteção.
 *
 * Não inicia o serviço de primeiro plano: a partir do Android 12 um job em
 * segundo plano não pode chamar `startForegroundService`. O papel deste worker
 * é detectar que a proteção parou e avisar o usuário, cabendo ao boot receiver
 * e ao serviço de acessibilidade religá-la.
 */
class SendaHealthWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val permissions = FocoPermissions(applicationContext)
        if (!permissions.current().accessibilityEnabled) return Result.success()

        val lastHeartbeat = ServiceHeartbeatStore(applicationContext).last()
        if (!HeartbeatHealth.isStale(System.currentTimeMillis(), lastHeartbeat)) {
            return Result.success()
        }

        notifyProtectionStopped()
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun notifyProtectionStopped() {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return

        manager.createNotificationChannel(healthChannel())

        val openApp = PendingIntent.getActivity(
            applicationContext,
            REQUEST_CODE_OPEN_APP,
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_HEALTH)
            .setSmallIcon(R.drawable.ic_senda_notification)
            .setColor(ContextCompat.getColor(applicationContext, R.color.accent_sage))
            .setContentTitle(applicationContext.getString(R.string.notification_health_title))
            .setContentText(applicationContext.getString(R.string.notification_health_text))
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID_HEALTH, notification) }
    }

    private fun healthChannel(): NotificationChannel =
        NotificationChannel(
            CHANNEL_HEALTH,
            applicationContext.getString(R.string.notification_channel_health),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = applicationContext.getString(
                R.string.notification_channel_health_description
            )
        }

    private companion object {
        const val CHANNEL_HEALTH = "senda_health"
        const val NOTIFICATION_ID_HEALTH = 1003
        const val REQUEST_CODE_OPEN_APP = 20
    }
}
