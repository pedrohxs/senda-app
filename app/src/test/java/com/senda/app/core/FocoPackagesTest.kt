package com.senda.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocoPackagesTest {

    @Test
    fun reconheceBancosEAutenticadoresComoSensiveis() {
        assertTrue(FocoPackages.isSensitive("com.nu.production"))
        assertTrue(FocoPackages.isSensitive("com.itau"))
        assertTrue(FocoPackages.isSensitive("br.gov.meugovbr"))
        assertTrue(FocoPackages.isSensitive("com.google.android.apps.authenticator2"))
        assertTrue(FocoPackages.isSensitive("com.bitwarden"))
    }

    @Test
    fun naoClassificaAppsComunsComoSensiveis() {
        assertFalse(FocoPackages.isSensitive("com.instagram.android"))
        assertFalse(FocoPackages.isSensitive("com.whatsapp"))
        assertFalse(FocoPackages.isSensitive("com.android.chrome"))
    }

    @Test
    fun reconheceOsNavegadoresMapeados() {
        assertTrue(FocoPackages.isBrowser("com.android.chrome"))
        assertTrue(FocoPackages.isBrowser("org.mozilla.firefox"))
        assertTrue(FocoPackages.isBrowser("com.sec.android.app.sbrowser"))
        assertFalse(FocoPackages.isBrowser("com.instagram.android"))
    }

    @Test
    fun reconheceNavegadoresAlternativos() {
        assertTrue(FocoPackages.isBrowser("com.kiwibrowser.browser"))
        assertTrue(FocoPackages.isBrowser("com.vivaldi.browser"))
        assertTrue(FocoPackages.isBrowser("org.cromite.cromite"))
        assertTrue(FocoPackages.isBrowser("org.mozilla.focus"))
        assertTrue(FocoPackages.isBrowser("org.torproject.torbrowser"))
        assertTrue(FocoPackages.isBrowser("com.ecosia.android"))
    }

    @Test
    fun navegadoresNaoSaoTratadosComoSensiveis() {
        assertFalse(FocoPackages.isSensitive("com.kiwibrowser.browser"))
        assertFalse(FocoPackages.isSensitive("org.torproject.torbrowser"))
    }
}
