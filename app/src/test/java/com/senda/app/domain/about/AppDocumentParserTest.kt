package com.senda.app.domain.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDocumentParserTest {

    @Test
    fun separaTitulosParagrafosEItens() {
        val text = """
            # Documento

            ## Primeira parte
            Um parágrafo solto.

            - item um
            - item dois

            ## Segunda parte
            Outro parágrafo.
        """.trimIndent()

        val sections = AppDocumentParser.sections(text)

        assertEquals(2, sections.size)
        assertEquals("Primeira parte", sections[0].title)
        assertEquals(listOf("Um parágrafo solto."), sections[0].paragraphs)
        assertEquals(listOf("item um", "item dois"), sections[0].bullets)
        assertEquals("Segunda parte", sections[1].title)
        assertEquals(listOf("Outro parágrafo."), sections[1].paragraphs)
    }

    @Test
    fun ignoraLinhasEmBrancoEEspacosSobrando() {
        val text = "## Título\n\n   \n- item com espaço   \n"

        val sections = AppDocumentParser.sections(text)

        assertEquals(1, sections.size)
        assertEquals(listOf("item com espaço"), sections[0].bullets)
        assertTrue(sections[0].paragraphs.isEmpty())
    }

    @Test
    fun changelogLeVersaoEData() {
        val text = """
            # Novidades

            ## 0.2.0 · 2026-10-30
            - primeiro item
            - segundo item

            ## 0.1.11 · 2026-10-24
            - item antigo
        """.trimIndent()

        val entries = AppDocumentParser.changelog(text)

        assertEquals(listOf("0.2.0", "0.1.11"), entries.map { it.version })
        assertEquals("2026-10-30", entries[0].date)
        assertEquals(listOf("primeiro item", "segundo item"), entries[0].bullets)
    }

    @Test
    fun changelogSemDataContinuaValido() {
        val entries = AppDocumentParser.changelog("## 1.0.0\n- feito\n")

        assertEquals("1.0.0", entries.single().version)
        assertEquals("", entries.single().date)
    }

    @Test
    fun secaoSemItensEhIgnoradaNoChangelog() {
        val entries = AppDocumentParser.changelog(
            """
            ## 1.0.0
            Só um resumo, sem itens.
            """.trimIndent()
        )

        assertTrue(entries.isEmpty())
    }

    @Test
    fun documentoVazioNaoQuebra() {
        assertTrue(AppDocumentParser.sections("").isEmpty())
        assertTrue(AppDocumentParser.changelog("").isEmpty())
    }
}
