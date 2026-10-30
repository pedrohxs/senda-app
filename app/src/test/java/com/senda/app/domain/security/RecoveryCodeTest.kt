package com.senda.app.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RecoveryCodeTest {

    @Test
    fun codigoGeradoTemOTamanhoEUsaAlfabetoSemAmbiguidade() {
        val code = RecoveryCode.generate(Random(42))

        assertEquals(8, code.length)
        assertFalse(code.contains('0'))
        assertFalse(code.contains('O'))
        assertFalse(code.contains('1'))
        assertFalse(code.contains('I'))
        assertTrue(RecoveryCode.isWellFormed(code))
    }

    @Test
    fun normalizacaoAceitaMinusculasEspacosEHifen() {
        assertEquals("ABCDEFGH", RecoveryCode.normalize("abcd efgh"))
        assertEquals("ABCDEFGH", RecoveryCode.normalize("abcd-efgh "))
        assertEquals("ABCDEFGH", RecoveryCode.normalize("AB CD - EF GH"))
    }

    @Test
    fun formatacaoQuebraEmDoisBlocos() {
        assertEquals("ABCD-EFGH", RecoveryCode.format("abcdefgh"))
        assertEquals("ABCD-EFGH", RecoveryCode.format("ABCD-EFGH"))
    }

    @Test
    fun codigoIncompletoNaoEhValido() {
        assertFalse(RecoveryCode.isWellFormed("ABCD"))
        assertFalse(RecoveryCode.isWellFormed(""))
        assertTrue(RecoveryCode.isWellFormed("ABCD-EFGH"))
    }

    @Test
    fun normalizacaoDescartaCaracteresInvalidos() {
        // 1/0 confundem com I/O e são descartados na digitação.
        assertEquals("ABCD", RecoveryCode.normalize("AB1CD0"))
    }
}
