package com.senda.app.data.repository

import androidx.room.withTransaction
import com.senda.app.core.UsageKey
import com.senda.app.data.local.SendaDatabase
import com.senda.app.data.local.entity.DailyGoalEntity
import com.senda.app.data.local.entity.ReportDayEntity
import com.senda.app.data.local.entity.ReportMonitorCheckpointEntity
import com.senda.app.data.local.entity.TargetType
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.data.local.entity.LimitRules
import com.senda.app.domain.policy.GroupPolicy
import com.senda.app.domain.policy.GroupRuleResolver
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.util.Log

/** Coverage is evidence of monitoring, independent of whether any app was used. */
class ReportHistoryRepository(
    private val database: SendaDatabase,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val dao get() = database.reportHistoryDao()
    private val processToken = UUID.randomUUID().toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun recordHeartbeatAsync(epochMillis: Long, elapsedRealtime: Long, bootCount: Int, healthy: Boolean, screenOff: Boolean) {
        scope.launch {
            runCatching { recordHeartbeat(epochMillis, elapsedRealtime, bootCount, healthy, screenOff) }
                .onFailure { Log.e("ReportHistory", "Não foi possível registrar cobertura do monitoramento", it) }
        }
    }

    suspend fun recordHeartbeat(epochMillis: Long, elapsedRealtime: Long, bootCount: Int, healthy: Boolean, screenOff: Boolean = false) {
        database.withTransaction {
            val zone = clock.zone
            val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
            val previous = dao.checkpoint()
            if (previous != null && previous.bootCount == bootCount && elapsedRealtime < previous.elapsedRealtime) return@withTransaction
            val hasApps = database.appGroupDao().enabledGroups().any { group ->
                database.groupItemDao().getByGroup(group.id).any { it.targetType == TargetType.APP }
            }
            val monitoring = healthy && hasApps
            val current = ReportMonitorCheckpointEntity(epochMillis = epochMillis,
                elapsedRealtime = elapsedRealtime, bootCount = bootCount, healthy = monitoring, zoneId = zone.id,
                screenOff = screenOff, processToken = processToken)
            if ((dao.latestDate() ?: date.toString()) > date.toString()) {
                dao.saveCheckpoint(current.copy(healthy = false))
                return@withTransaction
            }
            val elapsed = elapsedRealtime - (previous?.elapsedRealtime ?: elapsedRealtime)
            val wallElapsed = epochMillis - (previous?.epochMillis ?: epochMillis)
            val knownScreenOff = previous?.screenOff == true && previous.processToken == processToken
            if (previous != null && previous.healthy && monitoring && previous.bootCount == bootCount &&
                previous.zoneId == zone.id && elapsed > 0L && (elapsed <= 90_000L || knownScreenOff) && wallElapsed > 0L &&
                abs(wallElapsed - elapsed) <= 2_000L) {
                var cursor = previous.epochMillis
                while (cursor < epochMillis) {
                    val sliceDate = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                    val end = minOf(epochMillis, sliceDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                    val day = ensureDay(sliceDate, zone)
                    // A change of time zone cannot establish full coverage for an old day.
                    if (day.zoneId == zone.id) {
                        dao.saveDay(day.copy(coveredMillis = (day.coveredMillis + end - cursor).coerceAtMost(day.expectedMillis)))
                    }
                    cursor = end
                }
            }
            ensureDay(date, zone)
            if (monitoring) captureGoalsInternal(date, zone)
            dao.saveCheckpoint(current)
        }
    }

    /** The current day's final configuration becomes its historical goal after midnight. */
    suspend fun captureGoals() {
        database.withTransaction {
            val date = LocalDate.now(clock)
            if ((dao.latestDate() ?: date.toString()) <= date.toString()) captureGoalsInternal(date, clock.zone)
        }
    }

    private suspend fun captureGoalsInternal(date: LocalDate, zone: ZoneId) {
        val snapshots = database.appGroupDao().enabledGroups().flatMap { group ->
            val apps = database.groupItemDao().getByGroup(group.id).filter { it.targetType == TargetType.APP }
            val schedules = database.groupScheduleDao().getForGroup(group.id)
            val policy = GroupPolicy(group, apps, schedules, null)
            // Rules can change only at midnight or a window boundary, including overnight ends.
            val candidates = listOf(date.atStartOfDay()) + schedules.flatMap {
                listOf(date.atTime(it.startHour, it.startMinute), date.atTime(it.endHour, it.endMinute))
            }
            val rules = candidates.map { GroupRuleResolver.resolve(policy, it) }
                .filter { it.scheduleType == ScheduleType.LIMITED }.distinctBy { it.scopeKey }
                .map { rule ->
                    val label = if (rule.scopeKey == group.id) group.name else schedules.firstOrNull {
                        UsageKey.forLimit(group.id, it.ruleId) == rule.scopeKey
                    }?.name?.takeIf { it.isNotBlank() } ?: "Limite personalizado"
                    Triple(rule.scopeKey, LimitRules.from(rule.group), label)
                }
            rules.flatMap { (scope, config, label) ->
                if (config.sharedLimitAcrossApps) listOf(DailyGoalEntity(date.toString(), scope, group.id, group.name,
                    label, config.dailyLimitMinutes * 60, true))
                else apps.map { item -> DailyGoalEntity(date.toString(), UsageKey.forTarget(scope, item.targetIdentifier), group.id,
                    group.name, if (scope == group.id) item.displayName else "$label • ${item.displayName}", config.dailyLimitMinutes * 60, false) }
            }
        }.sortedBy { it.usageKey }
        val day = ensureDay(date, zone)
        val previous = dao.goalsForDate(date.toString())
        val previousModes = previous.associate { UsageKey.scopeKey(it.usageKey) to it.shared }
        val changedAccounting = snapshots.any { UsageKey.scopeKey(it.usageKey) in previousModes && previousModes[UsageKey.scopeKey(it.usageKey)] != it.shared }
        val updated = day.copy(goalsRecorded = true, goalsReliable = day.goalsReliable && !changedAccounting)
        if (day != updated) dao.saveDay(updated)
        if (previous != snapshots) {
            dao.deleteGoals(date.toString())
            if (snapshots.isNotEmpty()) dao.saveGoals(snapshots)
        }
    }

    private suspend fun ensureDay(date: LocalDate, zone: ZoneId): ReportDayEntity {
        dao.findDay(date.toString())?.let { return it }
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return ReportDayEntity(date.toString(), 0L, end - start, false, zone.id).also { dao.saveDay(it) }
    }
}
