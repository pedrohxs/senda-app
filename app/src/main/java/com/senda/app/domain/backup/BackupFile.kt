package com.senda.app.domain.backup

import kotlinx.serialization.Serializable

/**
 * Formato do arquivo de backup do Senda.
 *
 * O arquivo é JSON legível de propósito: se um dia o app deixar de existir, os
 * dados continuam recuperáveis à mão. Tudo o que é estado do aparelho (sessão
 * em andamento, cooldowns, âncora do relógio) fica de fora — só o que é
 * configuração e histórico do usuário entra aqui.
 *
 * Regras de compatibilidade:
 * - [formatVersion] só aumenta quando o formato muda de forma incompatível;
 * - campos novos sempre entram com valor padrão, então um arquivo antigo
 *   continua abrindo em versões novas do app;
 * - um arquivo de uma versão mais nova é recusado com mensagem clara em vez de
 *   ser importado pela metade.
 */
const val BACKUP_FORMAT = "senda-backup"

const val BACKUP_FORMAT_VERSION = 1

@Serializable
data class BackupFile(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val exportedAt: String = "",
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val data: BackupData = BackupData()
) {

    val isSupported: Boolean
        get() = format == BACKUP_FORMAT &&
            formatVersion >= 1 &&
            formatVersion <= BACKUP_FORMAT_VERSION

    val summary: BackupSummary
        get() = BackupSummary(
            exportedAt = exportedAt,
            appVersionName = appVersionName,
            groups = data.groups.size,
            targets = data.items.size,
            schedules = data.schedules.size,
            usageDays = data.usage.map { it.date }.distinct().size,
            challengeReasons = data.challengeReasons.size,
            pinWasConfigured = data.preferences.pinConfigured
        )
}

data class BackupSummary(
    val exportedAt: String,
    val appVersionName: String,
    val groups: Int,
    val targets: Int,
    val schedules: Int,
    val usageDays: Int,
    val challengeReasons: Int,
    val pinWasConfigured: Boolean
) {
    val hasContent: Boolean get() = groups > 0 || targets > 0 || usageDays > 0
}

@Serializable
data class BackupData(
    val groups: List<BackupGroup> = emptyList(),
    val items: List<BackupItem> = emptyList(),
    val schedules: List<BackupSchedule> = emptyList(),
    val usage: List<BackupUsage> = emptyList(),
    val challengeReasons: List<BackupChallengeReason> = emptyList(),
    val reportDays: List<BackupReportDay> = emptyList(),
    val dailyGoals: List<BackupDailyGoal> = emptyList(),
    val preferences: BackupPreferences = BackupPreferences()
)

@Serializable
data class BackupGroup(
    val id: String = "",
    val name: String = "",
    val colorHex: String = "#A1B976",
    val isEnabled: Boolean = true,
    val dailyLimitMinutes: Int = 30,
    val sessionLimitMinutes: Int = 5,
    val cooldownMinutes: Int = 10,
    val maxDailyOpens: Int = 15,
    val sharedLimitAcrossApps: Boolean = true,
    val sessionCountMode: String = "ACTIVE_SCREEN_TIME",
    val interventionType: String = "BREATHING",
    val breathingSeconds: Int = 10,
    val mathDifficulty: String = "EASY",
    val mathEquationCount: Int = 3,
    val progressiveFrictionEnabled: Boolean = true,
    val progressiveSecondsPerOpen: Int = 5,
    val alternativeAppPackage: String? = null,
    val temporaryDisableUntil: Long? = null
)

@Serializable
data class BackupItem(
    val groupId: String = "",
    val targetType: String = "APP",
    val targetIdentifier: String = "",
    val displayName: String = ""
)

@Serializable
data class BackupSchedule(
    val groupId: String = "",
    val scheduleType: String = "LIMITED",
    val daysOfWeek: List<Int> = emptyList(),
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val endHour: Int = 0,
    val endMinute: Int = 0,
    val ruleId: String = "",
    val name: String = "",
    val customRules: BackupLimitRules? = null
)

@Serializable
data class BackupLimitRules(
    val dailyLimitMinutes: Int = 30,
    val sessionLimitMinutes: Int = 5,
    val cooldownMinutes: Int = 10,
    val maxDailyOpens: Int = 15,
    val sharedLimitAcrossApps: Boolean = true,
    val sessionCountMode: String = "ACTIVE_SCREEN_TIME",
    val interventionType: String = "BREATHING",
    val breathingSeconds: Int = 10,
    val mathDifficulty: String = "EASY",
    val mathEquationCount: Int = 3,
    val progressiveFrictionEnabled: Boolean = true,
    val progressiveSecondsPerOpen: Int = 5,
    val alternativeAppPackage: String? = null
)

@Serializable
data class BackupUsage(
    val date: String = "",
    val groupId: String = "",
    val totalTimeUsedSeconds: Int = 0,
    val totalOpens: Int = 0,
    val impulsesAborted: Int = 0
)

@Serializable
data class BackupChallengeReason(
    val packageName: String = "",
    val displayName: String = "",
    val groupId: String = "",
    val reason: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class BackupReportDay(
    val date: String = "",
    val coveredMillis: Long = 0L,
    val expectedMillis: Long = 0L,
    val goalsRecorded: Boolean = false,
    val zoneId: String = "",
    val goalsReliable: Boolean = true
)

@Serializable
data class BackupDailyGoal(
    val date: String = "",
    val usageKey: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val targetName: String = "",
    val limitSeconds: Int = 0,
    val shared: Boolean = true
)

/**
 * Preferências que fazem parte da configuração do usuário. O PIN do guardião
 * NUNCA é exportado: é uma credencial e o hash de 4 dígitos é fácil de quebrar.
 * [pinConfigured] serve só para o app avisar que a proteção precisa ser
 * reconfigurada depois de restaurar.
 */
@Serializable
data class BackupPreferences(
    val clockGuardEnabled: Boolean = true,
    val editDelayEnabled: Boolean = false,
    val pinConfigured: Boolean = false
)
