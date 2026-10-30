package com.senda.app.domain.backup

import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.LimitRules
import com.senda.app.data.local.entity.MathDifficulty
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.data.local.entity.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMapperTest {

    @Test
    fun grupoVaiEVoltaComOsMesmosValores() {
        val backup = BackupGroup(
            id = "g1",
            name = "Redes sociais",
            colorHex = "#A1B976",
            dailyLimitMinutes = 40,
            sessionLimitMinutes = 7,
            cooldownMinutes = 12,
            maxDailyOpens = 6,
            sharedLimitAcrossApps = false,
            sessionCountMode = "WALL_CLOCK_WINDOW",
            interventionType = "REASON",
            breathingSeconds = 25,
            mathDifficulty = "HARD",
            mathEquationCount = 9,
            progressiveFrictionEnabled = false,
            progressiveSecondsPerOpen = 8,
            alternativeAppPackage = "com.google.android.youtube"
        )

        val entity = BackupMapper.groupFromBackup(backup)
        assertNotNull(entity)
        val restored = BackupMapper.groupToBackup(entity!!)

        assertEquals(backup.copy(temporaryDisableUntil = null), restored)
    }

    @Test
    fun grupoSemIdEhDescartado() {
        assertNull(BackupMapper.groupFromBackup(BackupGroup(id = "", name = "x")))
    }

    @Test
    fun valoresForaDaFaixaSaoLimitados() {
        val entity = BackupMapper.groupFromBackup(
            BackupGroup(
                id = "g1",
                name = "  ",
                colorHex = "vermelho",
                dailyLimitMinutes = -5,
                sessionLimitMinutes = 0,
                breathingSeconds = 1,
                mathEquationCount = 900,
                progressiveSecondsPerOpen = 0
            )
        )

        assertEquals("Grupo", entity!!.name)
        assertEquals("#A1B976", entity.colorHex)
        assertEquals(0, entity.dailyLimitMinutes)
        assertEquals(1, entity.sessionLimitMinutes)
        assertEquals(5, entity.breathingSeconds)
        assertEquals(20, entity.mathEquationCount)
        assertEquals(1, entity.progressiveSecondsPerOpen)
    }

    @Test
    fun enumsDesconhecidosCaemNoPadrao() {
        val entity = BackupMapper.groupFromBackup(
            BackupGroup(
                id = "g1",
                name = "x",
                interventionType = "PUNISHMENT",
                mathDifficulty = "IMPOSSIBLE",
                sessionCountMode = "RANDOM"
            )
        )!!

        assertEquals(InterventionType.BREATHING, entity.interventionType)
        assertEquals(MathDifficulty.EASY, entity.mathDifficulty)
        assertEquals(SessionCountMode.ACTIVE_SCREEN_TIME, entity.sessionCountMode)
    }

    @Test
    fun itemPrecisaDeIdentificadorENomeVazioUsaOIdentificador() {
        assertNull(BackupMapper.itemFromBackup(BackupItem(groupId = "g1", targetIdentifier = " ")))

        val entity = BackupMapper.itemFromBackup(
            BackupItem(groupId = "g1", targetType = "WEBSITE", targetIdentifier = "instagram.com")
        )!!

        assertEquals(TargetType.WEBSITE, entity.targetType)
        assertEquals("instagram.com", entity.displayName)
    }

    @Test
    fun horarioSemRegrasPropriasFicaSemRegras() {
        val entity = BackupMapper.scheduleFromBackup(BackupSchedule(groupId = "g1"))!!

        assertNull(entity.customRules)
    }

    @Test
    fun horarioComRegrasForaDaFaixaSaoCorrigidas() {
        val entity = BackupMapper.scheduleFromBackup(
            BackupSchedule(
                groupId = "g1",
                customRules = BackupLimitRules(sessionLimitMinutes = 999, dailyLimitMinutes = -1)
            )
        )!!

        assertEquals(240, entity.customRules?.sessionLimitMinutes)
        assertEquals(0, entity.customRules?.dailyLimitMinutes)
    }

    @Test
    fun horarioNormalizaDiasEHoras() {
        val entity = BackupMapper.scheduleFromBackup(
            BackupSchedule(
                groupId = "g1",
                scheduleType = "STRICT",
                daysOfWeek = listOf(0, 1, 1, 9, 7),
                startHour = 42,
                startMinute = -3,
                endHour = 99,
                endMinute = 75,
                ruleId = ""
            )
        )!!

        assertEquals(ScheduleType.STRICT, entity.scheduleType)
        assertEquals(listOf(1, 7), entity.daysOfWeek)
        assertEquals(23, entity.startHour)
        assertEquals(0, entity.startMinute)
        assertEquals(23, entity.endHour)
        assertEquals(59, entity.endMinute)
        assertTrue(entity.ruleId.isNotBlank())
    }

    @Test
    fun usoRecalculaMinutosAPartirDosSegundos() {
        val entity = BackupMapper.usageFromBackup(
            BackupUsage(date = "2026-10-30", groupId = "g1", totalTimeUsedSeconds = 185)
        )!!

        assertEquals(3, entity.totalTimeUsedMinutes)
        assertEquals(185, entity.totalTimeUsedSeconds)
    }

    @Test
    fun usoComDataInvalidaEhDescartado() {
        assertNull(BackupMapper.usageFromBackup(BackupUsage(date = "30/10/2026", groupId = "g1")))
        assertNull(BackupMapper.usageFromBackup(BackupUsage(date = "2026-10-30", groupId = "")))
    }

    @Test
    fun motivoVazioEhDescartado() {
        assertNull(
            BackupMapper.reasonFromBackup(
                BackupChallengeReason(packageName = "com.x", reason = "  ")
            )
        )
    }

    @Test
    fun regrasCompletasVaoEVoltam() {
        val rules = LimitRules(
            dailyLimitMinutes = 90,
            sessionLimitMinutes = 20,
            cooldownMinutes = 5,
            maxDailyOpens = 3,
            sharedLimitAcrossApps = false,
            sessionCountMode = SessionCountMode.WALL_CLOCK_WINDOW,
            interventionType = InterventionType.MATH,
            breathingSeconds = 30,
            mathDifficulty = MathDifficulty.HARD,
            mathEquationCount = 12,
            progressiveFrictionEnabled = false,
            progressiveSecondsPerOpen = 15,
            alternativeAppPackage = null
        )

        assertEquals(rules, BackupMapper.rulesFromBackup(BackupMapper.rulesToBackup(rules)))
    }
}
