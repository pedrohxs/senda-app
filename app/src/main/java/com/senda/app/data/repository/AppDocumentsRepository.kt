package com.senda.app.data.repository

import android.content.Context
import com.senda.app.domain.about.AppDocumentParser
import com.senda.app.domain.about.AppDocumentSection
import com.senda.app.domain.about.ChangelogEntry

/**
 * Textos que o app mostra sobre si mesmo: novidades, privacidade e licenças.
 *
 * Ficam em `assets` (dentro do APK) e são lidos do aparelho, sem rede. Se um
 * arquivo faltar ou vier corrompido, a tela mostra um estado de erro em vez de
 * uma lista vazia sem explicação.
 */
class AppDocumentsRepository(private val context: Context) {

    fun changelog(): Result<List<ChangelogEntry>> = read(CHANGELOG_FILE).map(AppDocumentParser::changelog)

    fun privacy(): Result<List<AppDocumentSection>> = read(PRIVACY_FILE).map(AppDocumentParser::sections)

    fun licenses(): Result<List<AppDocumentSection>> = read(LICENSE_FILE).map(AppDocumentParser::sections)

    private fun read(fileName: String): Result<String> = runCatching {
        context.assets.open(fileName).bufferedReader().use { it.readText() }
    }

    companion object {
        const val CHANGELOG_FILE = "CHANGELOG.md"
        const val PRIVACY_FILE = "PRIVACIDADE.md"
        const val LICENSE_FILE = "LICENCAS.md"
    }
}
