package com.senda.app.domain.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Leitura e escrita do arquivo de backup.
 *
 * Nada aqui conhece Android: o mesmo código roda nos testes JVM. As mensagens
 * de erro são códigos para a interface traduzir.
 */
object BackupCodec {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(raw: String): BackupReadResult {
        val text = raw.trim().trimStart('\uFEFF')
        if (text.isEmpty()) return BackupReadResult.Failure(BackupError.EMPTY)

        // O cabeçalho é lido antes do corpo para separar "não é backup do Senda"
        // de "backup de uma versão mais nova" de "arquivo corrompido".
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return BackupReadResult.Failure(BackupError.INVALID_JSON)

        val format = root["format"]?.jsonPrimitive?.contentOrNull
        if (format != BACKUP_FORMAT) return BackupReadResult.Failure(BackupError.NOT_A_BACKUP)

        val version = root["formatVersion"]?.jsonPrimitive?.intOrNull
            ?: return BackupReadResult.Failure(BackupError.INVALID_JSON)
        if (version > BACKUP_FORMAT_VERSION) {
            return BackupReadResult.Failure(BackupError.NEWER_VERSION)
        }
        if (version < 1) return BackupReadResult.Failure(BackupError.INVALID_JSON)

        val file = runCatching { json.decodeFromJsonElement(BackupFile.serializer(), root) }
            .getOrNull()
            ?: return BackupReadResult.Failure(BackupError.INVALID_JSON)

        if (!file.summary.hasContent) return BackupReadResult.Failure(BackupError.EMPTY)

        return BackupReadResult.Success(file)
    }

    fun decodeOrNull(raw: String): BackupFile? =
        (decode(raw) as? BackupReadResult.Success)?.file
}

sealed interface BackupReadResult {
    data class Success(val file: BackupFile) : BackupReadResult
    data class Failure(val error: BackupError) : BackupReadResult
}

enum class BackupError {
    EMPTY,
    INVALID_JSON,
    NOT_A_BACKUP,
    NEWER_VERSION,
    TOO_LARGE,
    IO
}
