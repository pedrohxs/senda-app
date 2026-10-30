package com.senda.app.data.repository

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String
)

class InstalledAppsRepository(private val context: Context) {

    suspend fun getLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        packageManager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { activityInfo ->
                InstalledApp(
                    packageName = activityInfo.packageName,
                    label = activityInfo.loadLabel(packageManager).toString()
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
