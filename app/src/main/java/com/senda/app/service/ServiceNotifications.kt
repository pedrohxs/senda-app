package com.senda.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.senda.app.MainActivity
import com.senda.app.R

/** Dados que aparecem na notificação da sessão. */
internal data class SessionNotification(
    val displayName: String,
    val remainingSeconds: Int,
    val durationSeconds: Int
)

/**
 * Notificações do serviço de proteção.
 *
 * Ficou separado do serviço para o serviço cuidar de sessão e bloqueio. Aqui
 * vive só o desenho da notificação: canal, textos, barra de progresso e os
 * atalhos de abrir o app e encerrar a sessão.
 */
internal class ServiceNotifications(private val context: Context) {

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.notification_channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_service_description)
            setShowBadge(false)
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.notification_channel_alerts),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_alerts_description)
        }

        manager.createNotificationChannels(listOf(serviceChannel, alertsChannel))
    }

    fun serviceNotification(session: SessionNotification?): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_senda_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent_sage))
            .setContentIntent(openAppIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (session == null) {
            builder
                .setContentTitle(context.getString(R.string.notification_title_active))
                .setContentText(context.getString(R.string.notification_text_active))
        } else {
            builder
                .setContentTitle(
                    context.getString(R.string.notification_title_session, session.displayName)
                )
                .setContentText(
                    context.getString(
                        R.string.notification_text_session,
                        formatRemaining(session.remainingSeconds)
                    )
                )
                .setProgress(
                    session.durationSeconds,
                    session.durationSeconds - session.remainingSeconds,
                    false
                )
                .addAction(
                    R.drawable.ic_senda_notification,
                    context.getString(R.string.notification_action_stop_session),
                    stopSessionIntent()
                )
        }

        builder.addAction(
            R.drawable.ic_senda_notification,
            context.getString(R.string.notification_action_open),
            openAppIntent()
        )

        return builder.build()
    }

    fun accessibilityAlert(): Notification {
        val settingsIntent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_ACCESSIBILITY_SETTINGS,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_senda_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent_sage))
            .setContentTitle(context.getString(R.string.notification_accessibility_disabled_title))
            .setContentText(context.getString(R.string.notification_accessibility_disabled_text))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun notify(notificationId: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(notificationId, notification) }
    }

    fun cancel(notificationId: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_APP,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun stopSessionIntent(): PendingIntent =
        PendingIntent.getService(
            context,
            REQUEST_CODE_STOP_SESSION,
            Intent(context, FocoForegroundService::class.java)
                .setAction(FocoActions.ACTION_STOP_SESSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val CHANNEL_SERVICE = "senda_service"
        const val CHANNEL_ALERTS = "senda_alerts"
        const val NOTIFICATION_ID_SERVICE = 1001
        const val NOTIFICATION_ID_WATCHDOG = 1002

        private const val REQUEST_CODE_OPEN_APP = 10
        private const val REQUEST_CODE_STOP_SESSION = 11
        private const val REQUEST_CODE_ACCESSIBILITY_SETTINGS = 12
    }
}
