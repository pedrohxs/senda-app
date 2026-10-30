package com.senda.app.data.repository

import com.senda.app.core.DiagnosticsLog
import com.senda.app.data.local.dao.DailyUsageLogDao
import com.senda.app.data.local.dao.ReportHistoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate

/**
 * Limpeza periódica do histórico.
 *
 * Sem retenção o banco cresce para sempre (um registro por grupo por dia, mais
 * o detalhe por app). Um ano é suficiente para relatórios e comparações; o que
 * é mais antigo sai, mantendo o aparelho leve.
 */
class DataMaintenance(
    private val usageDao: DailyUsageLogDao,
    private val reportHistoryDao: ReportHistoryDao,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    suspend fun pruneOldData(retentionDays: Int = DEFAULT_RETENTION_DAYS) = withContext(Dispatchers.IO) {
        if (retentionDays <= 0) return@withContext
        val cutoff = cutoffDate(LocalDate.now(clock), retentionDays)

        runCatching {
            usageDao.deleteBefore(cutoff)
            usageDao.deleteSessionUsageBefore(cutoff)
            reportHistoryDao.deleteDaysBefore(cutoff)
            reportHistoryDao.deleteGoalsBefore(cutoff)
            DiagnosticsLog.add("manutenção", "histórico anterior a $cutoff removido")
        }.onFailure { error ->
            DiagnosticsLog.add("manutenção", "falha ao limpar histórico: ${error.message}")
        }
    }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 365

        fun cutoffDate(today: LocalDate, retentionDays: Int): String =
            today.minusDays(retentionDays.toLong().coerceAtLeast(1L)).toString()
    }
}
