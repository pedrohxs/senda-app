package com.senda.app

import android.app.Application
import com.senda.app.core.DiagnosticsLog
import com.senda.app.di.AppContainer
import com.senda.app.service.HealthWorkerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SendaApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()
        DiagnosticsLog.add("app", "iniciado (versão $versionName)")
        HealthWorkerScheduler.schedule(this)
        startupScope.launch { container.dataMaintenance.pruneOldData() }
    }
}
