package com.senda.app.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import com.senda.app.core.DiagnosticsLog
import com.senda.app.data.local.SendaDatabase
import com.senda.app.data.preferences.ProtectionStore
import com.senda.app.data.preferences.SendaSettingsStore
import com.senda.app.domain.backup.BackupCodec
import com.senda.app.domain.backup.BackupData
import com.senda.app.domain.backup.BackupError
import com.senda.app.domain.backup.BackupFile
import com.senda.app.domain.backup.BackupMapper
import com.senda.app.domain.backup.BackupPreferences
import com.senda.app.domain.backup.BackupReadResult
import com.senda.app.domain.policy.DailyLimitLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Exportação e restauração de configurações e histórico.
 *
 * O arquivo carrega apenas dados do usuário. Ficam de fora, de propósito:
 * - o PIN do guardião (é credencial; hash de 4 dígitos é trivial de quebrar);
 * - a sessão em andamento, cooldowns e âncoras de relógio (estado do aparelho,
 *   que não faz sentido reaproveitar em outra instalação).
 */
class BackupRepository(
    private val resolver: ContentResolver,
    private val database: SendaDatabase,
    private val settingsStore: SendaSettingsStore,
    private val protectionStore: ProtectionStore,
    private val dailyLimitLatch: DailyLimitLatch,
    private val versionName: String,
    private val versionCode: Int,
    private val onBeforeRestore: suspend () -> Unit = {},
    private val clock: Clock = Clock.systemDefaultZone()
) {

    suspend fun snapshot(): BackupFile = withContext(Dispatchers.IO) {
        val groupDao = database.appGroupDao()
        val itemDao = database.groupItemDao()
        val scheduleDao = database.groupScheduleDao()
        val usageDao = database.dailyUsageLogDao()
        val reasonDao = database.challengeReasonDao()
        val reportDao = database.reportHistoryDao()

        BackupFile(
            exportedAt = Instant.now(clock).toString(),
            appVersionName = versionName,
            appVersionCode = versionCode,
            data = BackupData(
                groups = groupDao.allGroups().map(BackupMapper::groupToBackup),
                items = itemDao.allItems().map(BackupMapper::itemToBackup),
                schedules = scheduleDao.allSchedules().map(BackupMapper::scheduleToBackup),
                usage = usageDao.allUsage().map(BackupMapper::usageToBackup),
                challengeReasons = reasonDao.all().map(BackupMapper::reasonToBackup),
                reportDays = reportDao.allDays().map(BackupMapper::reportDayToBackup),
                dailyGoals = reportDao.allGoals().map(BackupMapper::dailyGoalToBackup),
                preferences = BackupPreferences(
                    clockGuardEnabled = protectionStore.clockGuardEnabled,
                    editDelayEnabled = protectionStore.editDelayEnabled,
                    pinConfigured = protectionStore.guardianPinSet
                )
            )
        )
    }

    suspend fun export(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = BackupCodec.encode(snapshot())
            resolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: error("Não foi possível abrir o arquivo para escrita.")
            settingsStore.setLastBackupAt(System.currentTimeMillis())
        }.isSuccess
    }

    suspend fun read(uri: Uri): BackupReadResult = withContext(Dispatchers.IO) {
        val text = runCatching {
            resolver.openInputStream(uri)?.use(::readLimited)
        }.getOrElse { error ->
            return@withContext if (error is BackupTooLargeException) {
                BackupReadResult.Failure(BackupError.TOO_LARGE)
            } else {
                BackupReadResult.Failure(BackupError.IO)
            }
        } ?: return@withContext BackupReadResult.Failure(BackupError.IO)

        BackupCodec.decode(text)
    }

    /**
     * Substitui todo o conteúdo local pelo do arquivo, numa única transação:
     * se algo falhar no meio, o banco continua como estava.
     */
    suspend fun restore(file: BackupFile): BackupRestoreSummary = withContext(Dispatchers.IO) {
        val data = file.data

        val groups = data.groups.mapNotNull(BackupMapper::groupFromBackup)
        val groupIds = groups.mapTo(HashSet()) { it.id }
        val items = data.items
            .mapNotNull(BackupMapper::itemFromBackup)
            .filter { it.groupId in groupIds }
            .distinctBy { it.groupId to it.targetIdentifier }
        val schedules = data.schedules
            .mapNotNull(BackupMapper::scheduleFromBackup)
            .filter { it.groupId in groupIds }
        val usage = data.usage.mapNotNull(BackupMapper::usageFromBackup)
        val reasons = data.challengeReasons.mapNotNull(BackupMapper::reasonFromBackup)
        val days = data.reportDays.mapNotNull(BackupMapper::reportDayFromBackup)
        val goals = data.dailyGoals.mapNotNull(BackupMapper::dailyGoalFromBackup)

        val groupDao = database.appGroupDao()
        val itemDao = database.groupItemDao()
        val scheduleDao = database.groupScheduleDao()
        val usageDao = database.dailyUsageLogDao()
        val reasonDao = database.challengeReasonDao()
        val reportDao = database.reportHistoryDao()

        // A sessão ativa precisa terminar antes da troca, senão ela gravaria uso
        // em um grupo que acabou de deixar de existir.
        onBeforeRestore()
        delay(RESTORE_SETTLE_MS)

        database.withTransaction {
            groupDao.deleteAll() // itens e horários saem junto (FK em cascata)
            usageDao.deleteAll()
            usageDao.deleteAllSessionUsage()
            reasonDao.deleteAll()
            reportDao.deleteAllDays()
            reportDao.deleteAllGoals()

            if (groups.isNotEmpty()) groupDao.upsertAll(groups)
            if (items.isNotEmpty()) itemDao.upsertAll(items)
            if (schedules.isNotEmpty()) scheduleDao.upsertAll(schedules)
            if (usage.isNotEmpty()) usageDao.insertAll(usage)
            if (reasons.isNotEmpty()) reasonDao.insertAll(reasons)
            if (days.isNotEmpty()) reportDao.saveDays(days)
            if (goals.isNotEmpty()) reportDao.saveAllGoals(goals)
        }

        usageDao.deleteOrphans()

        val preferences = data.preferences
        // As preferências ficam fora da transação do banco (DataStore e
        // SharedPreferences não participam dela). Uma falha aqui não pode
        // esconder que o histórico já foi restaurado com sucesso.
        runCatching {
            if (protectionStore.clockGuardEnabled != preferences.clockGuardEnabled) {
                protectionStore.setClockGuardEnabled(preferences.clockGuardEnabled)
            }
            if (protectionStore.editDelayEnabled != preferences.editDelayEnabled) {
                protectionStore.setEditDelayEnabled(preferences.editDelayEnabled)
            }
        }
        dailyLimitLatch.clear()

        BackupRestoreSummary(
            groups = groups.size,
            targets = items.size,
            schedules = schedules.size,
            usageDays = usage.map { it.date }.distinct().size,
            usageEntries = usage.size,
            challengeReasons = reasons.size,
            pinWasConfigured = preferences.pinConfigured
        )
    }

    /**
     * Cópia automática em `Download/Senda`, sem permissão de armazenamento.
     *
     * O usuário costuma lembrar do backup manual só depois de perder os dados.
     * Esta cópia roda uma vez por dia quando o app abre e mantém as 7 últimas.
     * Em Android < 10 (sem MediaStore.Downloads) ela não roda; o backup manual
     * continua disponível.
     */
    suspend fun exportAutoBackup(): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false

        runCatching {
            val text = BackupCodec.encode(snapshot())
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, autoBackupFileName())
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/Senda"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false

            val written = runCatching {
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(text.toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)

            if (!written) {
                runCatching { resolver.delete(uri, null, null) }
                return@withContext false
            }

            runCatching {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            }
            settingsStore.setLastAutoBackupAt(System.currentTimeMillis())
            DiagnosticsLog.add("backup", "cópia automática salva em Download/Senda")
            pruneAutoBackups()
            true
        }.getOrElse { error ->
            DiagnosticsLog.add("backup", "falha na cópia automática: ${error.message}")
            false
        }
    }

    /** Backup automático mais recente, se existir. */
    suspend fun latestAutoBackup(): BackupReadResult? = withContext(Dispatchers.IO) {
        val uri = queryAutoBackups().firstOrNull() ?: return@withContext null
        read(uri)
    }

    private suspend fun pruneAutoBackups() = withContext(Dispatchers.IO) {
        runCatching {
            queryAutoBackups().drop(AUTO_BACKUP_KEEP).forEach { uri ->
                resolver.delete(uri, null, null)
            }
        }
    }

    private fun queryAutoBackups(): List<Uri> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%Senda%")
        val order = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        val uris = mutableListOf<Uri>()
        runCatching {
            resolver.query(collection, projection, selection, args, order)?.use { cursor ->
                while (cursor.moveToNext() && uris.size < AUTO_BACKUP_KEEP * 2) {
                    uris += ContentUris.withAppendedId(collection, cursor.getLong(0))
                }
            }
        }
        return uris
    }

    private fun autoBackupFileName(): String {
        val stamp = AUTO_BACKUP_NAME_FORMAT.format(Instant.now(clock))
        return "senda-auto-$stamp.json"
    }

    private fun readLimited(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)

        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() > MAX_BACKUP_BYTES) throw BackupTooLargeException()
        }

        return output.toByteArray().toString(Charsets.UTF_8)
    }

    private class BackupTooLargeException : Exception()

    private companion object {
        const val MAX_BACKUP_BYTES = 32 * 1024 * 1024

        /** Tempo para a sessão encerrada gravar o uso dela antes da limpeza. */
        const val RESTORE_SETTLE_MS = 300L

        /** Quantas cópias automáticas ficam em Download/Senda. */
        const val AUTO_BACKUP_KEEP = 7

        val AUTO_BACKUP_NAME_FORMAT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmm")
            .withZone(ZoneId.systemDefault())
    }
}

data class BackupRestoreSummary(
    val groups: Int,
    val targets: Int,
    val schedules: Int,
    val usageDays: Int,
    val usageEntries: Int,
    val challengeReasons: Int,
    val pinWasConfigured: Boolean
)
