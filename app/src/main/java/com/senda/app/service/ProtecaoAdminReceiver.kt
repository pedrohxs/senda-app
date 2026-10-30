package com.senda.app.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.senda.app.R

class ProtecaoAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(
            context,
            context.getString(R.string.device_admin_enabled),
            Toast.LENGTH_SHORT
        ).show()
        notifyStateChanged(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(
            context,
            context.getString(R.string.device_admin_disabled),
            Toast.LENGTH_SHORT
        ).show()
        notifyStateChanged(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.device_admin_disable_warning)

    private fun notifyStateChanged(context: Context) {
        context.sendBroadcast(
            Intent(FocoActions.ACTION_DEVICE_ADMIN_CHANGED)
                .setPackage(context.packageName)
        )
    }
}

object ProtecaoAdmin {

    fun componentName(context: Context): ComponentName =
        ComponentName(context, ProtecaoAdminReceiver::class.java)

    fun isActive(context: Context): Boolean {
        val manager = context.getSystemService(DevicePolicyManager::class.java)
        return manager.isAdminActive(componentName(context))
    }

    fun activationIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.device_admin_explanation)
            )

    fun deactivate(context: Context) {
        val manager = context.getSystemService(DevicePolicyManager::class.java)
        manager.removeActiveAdmin(componentName(context))
    }
}
