package com.senda.app.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun sampleFile(): BackupFile = BackupFile(
        exportedAt = "2026-10-30T12:00:00Z",
        appVersionName = "0.1.8",
        appVersionCode = 9,
        data = BackupData(
            groups = listOf(
                BackupGroup(
                    id = "g1",
                    name = "Redes",
                    dailyLimitMinutes = 45,
                    interventionType = "MATH",
                    mathEquationCount = 5
                )
            ),
            items = listOf(
                BackupItem(
                    groupId = "g1",
                    targetType = "APP",
                    targetIdentifier = "com.instagram.android",
                    displayName = "Instagram"
                )
            ),
            schedules = listOf(
                BackupSchedule(
                    groupId = "g1",
                    scheduleType = "STRICT",
                    daysOfWeek = listOf(1, 2, 3, 4, 5),
                    startHour = 8,
                    startMinute = 30,
                    endHour = 18,
                    endMinute = 0,
                    customRules = BackupLimitRules(dailyLimitMinutes = 20)
                )
            ),
            usage = listOf(
                BackupUsage(
                    date = "2026-10-29",
                    groupId = "g1",
                    totalTimeUsedSeconds = 1800,
                    totalOpens = 4,
                    impulsesAborted = 2
                )
            ),
            challengeReasons = listOf(
                BackupChallengeReason(
                    packageName = "com.instagram.android",
                    displayName = "Instagram",
                    groupId = "g1",
                    reason = "ver stories",
                    createdAt = 1_700_000_000_000L
                )
            ),
            reportDays = listOf(
                BackupReportDay(
                    date = "2026-10-29",
                    coveredMillis = 60_000L,
                    expectedMillis = 60_000L,
                    goalsRecorded = true,
                    zoneId = "America/Fortaleza"
                )
            ),
            dailyGoals = listOf(
                BackupDailyGoal(
                    date = "2026-10-29",
                    usageKey = "g1",
                    groupId = "g1",
                    groupName = "Redes",
                    targetName = "Redes",
                    limitSeconds = 2700
                )
            ),
            preferences = BackupPreferences(
                    clockGuardEnabled = true,
                editDelayEnabled = true,
                pinConfigured = true
            )
        )
    )

    @Test
    fun idaEVoltaPreservaOsDados() {
        val original = sampleFile()

        val decoded = BackupCodec.decode(BackupCodec.encode(original))

        assertTrue(decoded is BackupReadResult.Success)
        assertEquals(original, (decoded as BackupReadResult.Success).file)
    }

    @Test
    fun resumoContaGruposAlvosDiasEPin() {
        val summary = sampleFile().summary

        assertEquals(1, summary.groups)
        assertEquals(1, summary.targets)
        assertEquals(1, summary.schedules)
        assertEquals(1, summary.usageDays)
        assertEquals(1, summary.challengeReasons)
        assertTrue(summary.pinWasConfigured)
        assertFalse(summary.exportedAt.isBlank())
    }

    @Test
    fun arquivoDeOutroAppEhRecusado() {
        val result = BackupCodec.decode("""{"hello":"world"}""")

        assertEquals(BackupReadResult.Failure(BackupError.NOT_A_BACKUP), result)
    }

    @Test
    fun versaoMaisNovaEhRecusada() {
        val json = """{"format":"senda-backup","formatVersion":99,"data":{"groups":[{"id":"g1","name":"x"}]}}"""

        val result = BackupCodec.decode(json)

        assertEquals(BackupReadResult.Failure(BackupError.NEWER_VERSION), result)
    }

    @Test
    fun arquivoVazioEhRecusado() {
        assertEquals(
            BackupReadResult.Failure(BackupError.EMPTY),
            BackupCodec.decode("   ")
        )

        val semDados = """{"format":"senda-backup","formatVersion":1,"data":{}}"""
        assertEquals(
            BackupReadResult.Failure(BackupError.EMPTY),
            BackupCodec.decode(semDados)
        )
    }

    @Test
    fun jsonInvalidoEhRecusado() {
        assertEquals(
            BackupReadResult.Failure(BackupError.INVALID_JSON),
            BackupCodec.decode("{isto não é json}")
        )
    }

    @Test
    fun camposDesconhecidosSaoIgnorados() {
        val json = """
            {
              "format": "senda-backup",
              "formatVersion": 1,
              "campoNovo": "ignorar",
              "data": {
                "groups": [
                  { "id": "g1", "name": "Redes", "campoNovoDoGrupo": 10 }
                ],
                "preferenciasNovas": { "x": 1 }
              }
            }
        """.trimIndent()

        val result = BackupCodec.decode(json)

        assertTrue(result is BackupReadResult.Success)
        assertEquals(1, (result as BackupReadResult.Success).file.data.groups.size)
    }

    @Test
    fun bomNoInicioEhIgnorado() {
        val json = "\uFEFF" + BackupCodec.encode(sampleFile())

        assertTrue(BackupCodec.decode(json) is BackupReadResult.Success)
    }
}
