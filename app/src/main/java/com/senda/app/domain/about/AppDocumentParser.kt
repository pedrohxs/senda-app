package com.senda.app.domain.about

/**
 * Um trecho de documento em texto simples, com título opcional e itens.
 *
 * Serve para o changelog, a política de privacidade e as licenças: os três são
 * arquivos locais (assets) e nenhum depende de rede para aparecer.
 */
data class AppDocumentSection(
    val title: String?,
    val paragraphs: List<String>,
    val bullets: List<String>
) {
    val isEmpty: Boolean get() = paragraphs.isEmpty() && bullets.isEmpty()
}

data class ChangelogEntry(
    val version: String,
    val date: String,
    val bullets: List<String>
)

object AppDocumentParser {

    private const val TITLE_PREFIX = "# "
    private const val SECTION_PREFIX = "## "
    private const val BULLET_PREFIX = "- "

    /** Quebra o documento em seções `##`, mantendo parágrafos e itens na ordem. */
    fun sections(text: String): List<AppDocumentSection> {
        val sections = mutableListOf<AppDocumentSection>()
        var title: String? = null
        val paragraphs = mutableListOf<String>()
        val bullets = mutableListOf<String>()

        fun flush() {
            if (title == null && paragraphs.isEmpty() && bullets.isEmpty()) return
            sections += AppDocumentSection(title, paragraphs.toList(), bullets.toList())
            paragraphs.clear()
            bullets.clear()
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith(TITLE_PREFIX) -> Unit
                line.startsWith(SECTION_PREFIX) -> {
                    flush()
                    title = line.removePrefix(SECTION_PREFIX).trim()
                }
                line.startsWith(BULLET_PREFIX) -> bullets += line.removePrefix(BULLET_PREFIX).trim()
                else -> paragraphs += line
            }
        }
        flush()
        return sections
    }

    /**
     * Lê o changelog no formato `## versão · data` seguido de itens.
     * Linhas antes da primeira versão (o título do arquivo) são ignoradas.
     */
    fun changelog(text: String): List<ChangelogEntry> = sections(text)
        .filter { it.title != null && it.bullets.isNotEmpty() }
        .mapNotNull { section ->
            val title = section.title ?: return@mapNotNull null
            val parts = title.split('·').map { it.trim() }
            val version = parts.firstOrNull().orEmpty()
            if (version.isEmpty()) return@mapNotNull null
            ChangelogEntry(
                version = version,
                date = parts.getOrNull(1).orEmpty(),
                bullets = section.bullets
            )
        }
}
