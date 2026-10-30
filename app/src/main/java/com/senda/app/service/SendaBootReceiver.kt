package com.senda.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.senda.app.data.permissions.FocoPermissions
import com.senda.app.domain.lifecycle.StartupPolicy

/**
 * Religa a proteção depois de um reboot ou de uma atualização do app.
 *
 * A acessibilidade costuma religar sozinha, mas o serviço de primeiro plano
 * não. Este receiver garante o ciclo completo e reagenda o worker de saúde.
 */
class SendaBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!isSupportedAction(intent.action)) return

        HealthWorkerScheduler.schedule(context)

        val permissions = FocoPermissions(context)
        if (!StartupPolicy.shouldStartServices(permissions.current().accessibilityEnabled)) {
            return
        }

        runCatching { FocoForegroundService.ensureRunning(context) }
    }

    private fun isSupportedAction(action: String?): Boolean = when (action) {
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        ACTION_QUICKBOOT_POWERON -> true

        else -> false
    }

    private companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
