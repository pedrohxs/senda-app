package com.senda.app.data.permissions

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.senda.app.service.FocoAccessibilityService
import com.senda.app.service.ProtecaoAdmin

data class SendaPermissionState(
    val accessibilityEnabled: Boolean,
    val overlayEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val deviceAdminEnabled: Boolean,
    val batteryOptimizationIgnored: Boolean
) {
    val essentialGranted: Boolean
        get() = accessibilityEnabled &&
            overlayEnabled &&
            notificationsEnabled
}

class FocoPermissions(private val context: Context) {

    fun current(): SendaPermissionState = SendaPermissionState(
        accessibilityEnabled = isAccessibilityEnabled(),
        overlayEnabled = Settings.canDrawOverlays(context),
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        deviceAdminEnabled = ProtecaoAdmin.isActive(context),
        batteryOptimizationIgnored = isBatteryOptimizationIgnored()
    )

    fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun accessibilityIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlayIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun deviceAdminIntent(): Intent =
        ProtecaoAdmin.activationIntent(context)

    fun appDetailsIntent(): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun deviceAdminSettingsIntent(): Intent =
        Intent(Settings.ACTION_SECURITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isAccessibilityEnabled(): Boolean {
        val expected = android.content.ComponentName(
            context,
            FocoAccessibilityService::class.java
        )
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (android.content.ComponentName.unflattenFromString(splitter.next()) == expected) {
                return true
            }
        }

        return false
    }
}
