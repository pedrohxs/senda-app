package com.senda.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlMatcherTest {

    @Test
    fun extraiHostDeUrlCompleta() {
        assertEquals("reddit.com", UrlMatcher.extractHost("https://www.reddit.com/r/android/"))
    }

    @Test
    fun extraiHostDeDominioSimples() {
        assertEquals("globo.com", UrlMatcher.extractHost("globo.com"))
    }

    @Test
    fun extraiSubdominio() {
        assertEquals("noticias.globo.com", UrlMatcher.extractHost("https://noticias.globo.com/mundo"))
    }

    @Test
    fun rejeitaTextoQueNaoEhUrl() {
        assertNull(UrlMatcher.extractHost("Pesquisar ou digitar URL"))
        assertNull(UrlMatcher.extractHost("17 + 28"))
        assertNull(UrlMatcher.extractHost("instagram"))
    }

    @Test
    fun bloqueiaDominioExatoESubdominios() {
        val blocked = setOf("reddit.com", "twitter.com")

        assertTrue(UrlMatcher.isBlocked("reddit.com", blocked))
        assertTrue(UrlMatcher.isBlocked("old.reddit.com", blocked))
        assertFalse(UrlMatcher.isBlocked("notreddit.com", blocked))
        assertFalse(UrlMatcher.isBlocked("globo.com", blocked))
    }
}
