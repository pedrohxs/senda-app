package com.senda.app.di

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.senda.app.data.local.SendaDatabase
import com.senda.app.data.permissions.FocoPermissions
import com.senda.app.data.preferences.CooldownStore
import com.senda.app.data.preferences.ActiveSessionStore
import com.senda.app.data.preferences.ProtectionStore
import com.senda.app.data.preferences.SendaSettingsStore
import com.senda.app.data.repository.AppDocumentsRepository
import com.senda.app.data.repository.AppLabels
import com.senda.app.data.repository.BackupRepository
import com.senda.app.data.repository.BlocksRepository
import com.senda.app.data.repository.DataMaintenance
import com.senda.app.data.repository.FocoPolicyRepository
import com.senda.app.data.repository.GroupRepository
import com.senda.app.data.repository.InstalledAppsRepository
import com.senda.app.data.repository.StatsRepository
import com.senda.app.data.repository.SessionCheckpointRepository
import com.senda.app.data.repository.ReportHistoryRepository
import com.senda.app.data.repository.ProtectionHealthRepository
import com.senda.app.data.preferences.ServiceHeartbeatStore
import com.senda.app.domain.policy.DailyLimitLatch
import com.senda.app.domain.policy.GroupStatusResolver
import com.senda.app.domain.security.ClockGuard
import com.senda.app.service.FocoForegroundService
import com.senda.app.service.ProtectionSelfTest

class AppContainer(context: Context) {

    private val database = SendaDatabase.getInstance(context)

    private val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()

    val groupRepository = GroupRepository(
        database = database,
        appGroupDao = database.appGroupDao(),
        groupItemDao = database.groupItemDao(),
        groupScheduleDao = database.groupScheduleDao(),
        dailyUsageLogDao = database.dailyUsageLogDao()
    )

    val protectionStore = ProtectionStore(context)

    val clockGuard = ClockGuard(protectionStore)

    val dailyLimitLatch = DailyLimitLatch()

    val cooldownStore = CooldownStore(context)

    val reportHistory = ReportHistoryRepository(database)

    val activeSessionStore = ActiveSessionStore(context)

    val sessionCheckpoints = SessionCheckpointRepository(
        activeSessionStore, database.dailyUsageLogDao(), cooldownStore
    )

    val policyRepository = FocoPolicyRepository(
        appGroupDao = database.appGroupDao(),
        groupItemDao = database.groupItemDao(),
        groupScheduleDao = database.groupScheduleDao(),
        dailyUsageLogDao = database.dailyUsageLogDao(),
        cooldownStore = cooldownStore,
        protectionStore = protectionStore
    )

    val installedAppsRepository = InstalledAppsRepository(context)

    val appLabels = AppLabels(context)

    val groupStatusResolver = GroupStatusResolver()

    val permissions = FocoPermissions(context)

    val heartbeatStore = ServiceHeartbeatStore(context)

    val protectionHealth = ProtectionHealthRepository(permissions, heartbeatStore)

    val protectionSelfTest = ProtectionSelfTest(context, permissions, heartbeatStore)

    val appDocuments = AppDocumentsRepository(context)

    val settingsStore = SendaSettingsStore(context)

    val statsRepository = StatsRepository(
        appGroupDao = database.appGroupDao(),
        dailyUsageLogDao = database.dailyUsageLogDao(),
        challengeReasonDao = database.challengeReasonDao(),
        reportHistoryDao = database.reportHistoryDao()
    )

    val blocksRepository = BlocksRepository(
        appGroupDao = database.appGroupDao(),
        groupItemDao = database.groupItemDao()
    )

    val dataMaintenance = DataMaintenance(
        usageDao = database.dailyUsageLogDao(),
        reportHistoryDao = database.reportHistoryDao()
    )

    val backupRepository = BackupRepository(
        resolver = context.applicationContext.contentResolver,
        database = database,
        settingsStore = settingsStore,
        protectionStore = protectionStore,
        dailyLimitLatch = dailyLimitLatch,
        versionName = packageInfo?.versionName.orEmpty(),
        versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it).toInt() } ?: 0,
        onBeforeRestore = { FocoForegroundService.stopSession(context.applicationContext) }
    )
}
