package com.senda.app.ui.usage

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Compartilha o CSV do relatório por qualquer app instalado (e-mail, Drive,
 * WhatsApp). O arquivo é escrito no cache do próprio Senda e exposto por um
 * FileProvider — nada é enviado para servidor nenhum.
 */
object ReportShare {

    fun share(context: Context, csv: String, subject: String) {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        prune(directory)
        val file = File(directory, "senda-relatorio-${System.currentTimeMillis()}.csv")
        file.writeText(csv, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, subject)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Mantém no máximo os cinco relatórios mais recentes no cache. */
    private fun prune(directory: File) {
        val files = directory.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        files.drop(MAX_CACHED_FILES).forEach { runCatching { it.delete() } }
    }

    private const val DIRECTORY = "reports"
    private const val MAX_CACHED_FILES = 5
}
