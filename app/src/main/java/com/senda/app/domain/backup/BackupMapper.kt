package com.senda.app.domain.backup

import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.ChallengeReasonEntity
import com.senda.app.data.local.entity.DailyGoalEntity
import com.senda.app.data.local.entity.DailyUsageLogEntity
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.LimitRules
import com.senda.app.data.local.entity.MathDifficulty
import com.senda.app.data.local.entity.ReportDayEntity
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.data.local.entity.TargetType
import java.util.UUID

/**
 * Conversão entre as entidades do banco e o formato do arquivo de backup.
 *
 * A conversão de volta é defensiva: um arquivo pode ter sido editado à mão ou
 * vir de uma versão diferente. Nada é aceito sem checagem — linhas inválidas
 * são descartadas em vez de derrubar a importação inteira.
 */
object BackupMapper {

    private const val DEFAULT_COLOR = "#A1B976"

    fun groupToBackup(entity: AppGroupEntity): BackupGroup = BackupGroup(
        id = entity.id,
        name = entity.name,
        colorHex = entity.colorHex,
        isEnabled = entity.isEnabled,
        dailyLimitMinutes = entity.dailyLimitMinutes,
        sessionLimitMinutes = entity.sessionLimitMinutes,
        cooldownMinutes = entity.cooldownMinutes,
        maxDailyOpens = entity.maxDailyOpens,
        sharedLimitAcrossApps = entity.sharedLimitAcrossApps,
        sessionCountMode = entity.sessionCountMode.name,
        interventionType = entity.interventionType.name,
        breathingSeconds = entity.breathingSeconds,
        mathDifficulty = entity.mathDifficulty.name,
        mathEquationCount = entity.mathEquationCount,
        progressiveFrictionEnabled = entity.progressiveFrictionEnabled,
        progressiveSecondsPerOpen = entity.progressiveSecondsPerOpen,
        alternativeAppPackage = entity.alternativeAppPackage,
        temporaryDisableUntil = entity.temporaryDisableUntil
    )

    fun groupFromBackup(backup: BackupGroup): AppGroupEntity? {
        if (backup.id.isBlank()) return null

        return AppGroupEntity(
            id = backup.id,
            name = backup.name.trim().ifBlank { "Grupo" },
            colorHex = backup.colorHex.takeIf { it.matches(HEX_COLOR) } ?: DEFAULT_COLOR,
            isEnabled = backup.isEnabled,
            dailyLimitMinutes = backup.dailyLimitMinutes.coerceIn(0, 1440),
            sessionLimitMinutes = backup.sessionLimitMinutes.coerceIn(1, 240),
            cooldownMinutes = backup.cooldownMinutes.coerceIn(0, 1440),
            maxDailyOpens = backup.maxDailyOpens.coerceIn(0, 999),
            sharedLimitAcrossApps = backup.sharedLimitAcrossApps,
            sessionCountMode = enumOr(backup.sessionCountMode, SessionCountMode.ACTIVE_SCREEN_TIME),
            interventionType = enumOr(backup.interventionType, InterventionType.BREATHING),
            breathingSeconds = backup.breathingSeconds.coerceIn(5, 600),
            mathDifficulty = enumOr(backup.mathDifficulty, MathDifficulty.EASY),
            mathEquationCount = backup.mathEquationCount.coerceIn(3, 20),
            progressiveFrictionEnabled = backup.progressiveFrictionEnabled,
            progressiveSecondsPerOpen = backup.progressiveSecondsPerOpen.coerceIn(1, 60),
            alternativeAppPackage = backup.alternativeAppPackage?.takeIf { it.isNotBlank() },
            temporaryDisableUntil = backup.temporaryDisableUntil?.takeIf { it > 0L }
        )
    }

    fun itemToBackup(entity: GroupItemEntity): BackupItem = BackupItem(
        groupId = entity.groupId,
        targetType = entity.targetType.name,
        targetIdentifier = entity.targetIdentifier,
        displayName = entity.displayName
    )

    fun itemFromBackup(backup: BackupItem): GroupItemEntity? {
        if (backup.groupId.isBlank()) return null
        if (backup.targetIdentifier.isBlank()) return null

        return GroupItemEntity(
            groupId = backup.groupId,
            targetType = enumOr(backup.targetType, TargetType.APP),
            targetIdentifier = backup.targetIdentifier.trim(),
            displayName = backup.displayName.trim().ifBlank { backup.targetIdentifier.trim() }
        )
    }

    fun scheduleToBackup(entity: GroupScheduleEntity): BackupSchedule = BackupSchedule(
        groupId = entity.groupId,
        scheduleType = entity.scheduleType.name,
        daysOfWeek = entity.daysOfWeek,
        startHour = entity.startHour,
        startMinute = entity.startMinute,
        endHour = entity.endHour,
        endMinute = entity.endMinute,
        ruleId = entity.ruleId,
        name = entity.name,
        customRules = entity.customRules?.let(::rulesToBackup)
    )

    fun scheduleFromBackup(backup: BackupSchedule): GroupScheduleEntity? {
        if (backup.groupId.isBlank()) return null

        return GroupScheduleEntity(
            groupId = backup.groupId,
            scheduleType = enumOr(backup.scheduleType, ScheduleType.LIMITED),
            daysOfWeek = backup.daysOfWeek.filter { it in 1..7 }.distinct().sorted(),
            startHour = backup.startHour.coerceIn(0, 23),
            startMinute = backup.startMinute.coerceIn(0, 59),
            endHour = backup.endHour.coerceIn(0, 23),
            endMinute = backup.endMinute.coerceIn(0, 59),
            ruleId = backup.ruleId.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = backup.name
        ).let { schedule ->
            val rules = backup.customRules?.let(::rulesFromBackup)
            if (rules == null || !rules.isValid) schedule else schedule.copy(customRules = rules)
        }
    }

    fun rulesToBackup(rules: LimitRules): BackupLimitRules = BackupLimitRules(
        dailyLimitMinutes = rules.dailyLimitMinutes,
        sessionLimitMinutes = rules.sessionLimitMinutes,
        cooldownMinutes = rules.cooldownMinutes,
        maxDailyOpens = rules.maxDailyOpens,
        sharedLimitAcrossApps = rules.sharedLimitAcrossApps,
        sessionCountMode = rules.sessionCountMode.name,
        interventionType = rules.interventionType.name,
        breathingSeconds = rules.breathingSeconds,
        mathDifficulty = rules.mathDifficulty.name,
        mathEquationCount = rules.mathEquationCount,
        progressiveFrictionEnabled = rules.progressiveFrictionEnabled,
        progressiveSecondsPerOpen = rules.progressiveSecondsPerOpen,
        alternativeAppPackage = rules.alternativeAppPackage
    )

    fun rulesFromBackup(backup: BackupLimitRules): LimitRules = LimitRules(
        dailyLimitMinutes = backup.dailyLimitMinutes.coerceIn(0, 1440),
        sessionLimitMinutes = backup.sessionLimitMinutes.coerceIn(1, 240),
        cooldownMinutes = backup.cooldownMinutes.coerceIn(0, 1440),
        maxDailyOpens = backup.maxDailyOpens.coerceIn(0, 999),
        sharedLimitAcrossApps = backup.sharedLimitAcrossApps,
        sessionCountMode = enumOr(backup.sessionCountMode, SessionCountMode.ACTIVE_SCREEN_TIME),
        interventionType = enumOr(backup.interventionType, InterventionType.BREATHING),
        breathingSeconds = backup.breathingSeconds.coerceIn(5, 600),
        mathDifficulty = enumOr(backup.mathDifficulty, MathDifficulty.EASY),
        mathEquationCount = backup.mathEquationCount.coerceIn(3, 20),
        progressiveFrictionEnabled = backup.progressiveFrictionEnabled,
        progressiveSecondsPerOpen = backup.progressiveSecondsPerOpen.coerceIn(1, 60),
        alternativeAppPackage = backup.alternativeAppPackage?.takeIf { it.isNotBlank() }
    )

    fun usageToBackup(entity: DailyUsageLogEntity): BackupUsage = BackupUsage(
        date = entity.date,
        groupId = entity.groupId,
        totalTimeUsedSeconds = entity.totalTimeUsedSeconds,
        totalOpens = entity.totalOpens,
        impulsesAborted = entity.impulsesAborted
    )

    fun usageFromBackup(backup: BackupUsage): DailyUsageLogEntity? {
        if (!backup.date.matches(ISO_DATE)) return null
        if (backup.groupId.isBlank()) return null

        val seconds = backup.totalTimeUsedSeconds.coerceAtLeast(0)
        return DailyUsageLogEntity(
            date = backup.date,
            groupId = backup.groupId,
            totalTimeUsedMinutes = seconds / 60,
            totalTimeUsedSeconds = seconds,
            totalOpens = backup.totalOpens.coerceAtLeast(0),
            impulsesAborted = backup.impulsesAborted.coerceAtLeast(0)
        )
    }

    fun reasonToBackup(entity: ChallengeReasonEntity): BackupChallengeReason = BackupChallengeReason(
        packageName = entity.packageName,
        displayName = entity.displayName,
        groupId = entity.groupId,
        reason = entity.reason,
        createdAt = entity.createdAt
    )

    fun reasonFromBackup(backup: BackupChallengeReason): ChallengeReasonEntity? {
        if (backup.reason.isBlank()) return null
        if (backup.packageName.isBlank()) return null

        return ChallengeReasonEntity(
            packageName = backup.packageName,
            displayName = backup.displayName,
            groupId = backup.groupId,
            reason = backup.reason,
            createdAt = backup.createdAt.coerceAtLeast(0L)
        )
    }

    fun reportDayToBackup(entity: ReportDayEntity): BackupReportDay = BackupReportDay(
        date = entity.date,
        coveredMillis = entity.coveredMillis,
        expectedMillis = entity.expectedMillis,
        goalsRecorded = entity.goalsRecorded,
        zoneId = entity.zoneId,
        goalsReliable = entity.goalsReliable
    )

    fun reportDayFromBackup(backup: BackupReportDay): ReportDayEntity? {
        if (!backup.date.matches(ISO_DATE)) return null

        return ReportDayEntity(
            date = backup.date,
            coveredMillis = backup.coveredMillis.coerceAtLeast(0L),
            expectedMillis = backup.expectedMillis.coerceAtLeast(0L),
            goalsRecorded = backup.goalsRecorded,
            zoneId = backup.zoneId,
            goalsReliable = backup.goalsReliable
        )
    }

    fun dailyGoalToBackup(entity: DailyGoalEntity): BackupDailyGoal = BackupDailyGoal(
        date = entity.date,
        usageKey = entity.usageKey,
        groupId = entity.groupId,
        groupName = entity.groupName,
        targetName = entity.targetName,
        limitSeconds = entity.limitSeconds,
        shared = entity.shared
    )

    fun dailyGoalFromBackup(backup: BackupDailyGoal): DailyGoalEntity? {
        if (!backup.date.matches(ISO_DATE)) return null
        if (backup.usageKey.isBlank()) return null

        return DailyGoalEntity(
            date = backup.date,
            usageKey = backup.usageKey,
            groupId = backup.groupId,
            groupName = backup.groupName,
            targetName = backup.targetName,
            limitSeconds = backup.limitSeconds.coerceAtLeast(0),
            shared = backup.shared
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: fallback

    private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val HEX_COLOR = Regex("""#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?""")
}
