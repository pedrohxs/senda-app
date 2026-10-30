package com.senda.app.data.preferences

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.domain.security.ClockDriftDetector
import com.senda.app.domain.security.ClockGuard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectionStoreTest {

    private lateinit var context: Context
    private lateinit var store: ProtectionStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences().edit().clear().commit()
        store = ProtectionStore(context)
    }

    @After
    fun tearDown() {
        preferences().edit().clear().commit()
    }

    @Test
    fun defineEValidaPinDoGuardiao() {
        store.setGuardianPin("1234")

        assertEquals(PinVerification.Success, store.verifyGuardianPin("1234"))
        assertEquals(PinVerification.Wrong(4), store.verifyGuardianPin("0000"))
    }

    @Test
    fun bloqueiaPinAposCincoTentativas() {
        store.setGuardianPin("1234")

        repeat(4) {
            assertTrue(store.verifyGuardianPin("0000") is PinVerification.Wrong)
        }

        assertTrue(store.verifyGuardianPin("0000") is PinVerification.Locked)
    }

    @Test
    fun atrasoDeEdicaoPersistePorAlvo() {
        store.requestEditDelay(
            target = "group-1",
            nowElapsedRealtime = 1_000L,
            bootCount = 7
        )

        assertEquals(
            EditUnlockState.Waiting(900L),
            store.editUnlockState(
                target = "group-1",
                nowElapsedRealtime = 1_000L,
                bootCount = 7
            )
        )
        assertEquals(
            EditUnlockState.Ready,
            store.editUnlockState(
                target = "group-1",
                nowElapsedRealtime = 1_000L + 15 * 60_000L,
                bootCount = 7
            )
        )
    }

    @Test
    fun bloqueioDeRelogioELiberadoQuandoHorarioEhRestaurado() {
        val bootCount = store.currentBootCount()
        val elapsed = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        store.saveClockAnchor(
            ClockDriftDetector.Anchor(
                wallMillis = wall,
                elapsedRealtime = elapsed,
                bootCount = bootCount,
                zoneOffsetSeconds = store.currentZoneOffsetSeconds(wall)
            )
        )
        store.startClockTamper(
            nowElapsedRealtime = elapsed,
            bootCount = bootCount,
            durationSeconds = 1_800L
        )

        val guard = ClockGuard(store)
        val tampered = guard.check(
            nowWallMillis = wall + 5 * 60_000L,
            nowElapsedRealtime = elapsed + 1_000L,
            bootCount = bootCount
        )
        assertTrue(tampered.tampered)

        val restored = guard.check(
            nowWallMillis = wall + 1_000L,
            nowElapsedRealtime = elapsed + 5_000L,
            bootCount = bootCount
        )
        assertEquals(false, restored.tampered)
    }

    @Test
    fun atrasoDeEdicaoEDescartadoAposReboot() {
        store.requestEditDelay(
            target = "group-1",
            nowElapsedRealtime = 1_000L,
            bootCount = 7
        )

        assertEquals(
            EditUnlockState.NotStarted,
            store.editUnlockState(
                target = "group-1",
                nowElapsedRealtime = 2_000L,
                bootCount = 8
            )
        )
    }

    @Test
    fun pinUsaPbkdf2EDepoisDeDefinido() {
        store.setGuardianPin("1234")

        assertEquals("pbkdf2v1", preferences().getString("guardian_pin_kdf", null))
        assertTrue((preferences().getInt("guardian_pin_iterations", 0)) >= 120_000)
        assertEquals(PinVerification.Success, store.verifyGuardianPin("1234"))
    }

    @Test
    fun codigoDeRecuperacaoRedefineOPin() {
        val recoveryCode = store.setGuardianPin("1234")
        assertTrue(recoveryCode.contains("-"))
        assertTrue(store.recoveryConfigured)

        assertEquals(PinVerification.Success, store.verifyRecoveryCode(recoveryCode))

        val newCode = store.setGuardianPin("9876")
        assertEquals(PinVerification.Success, store.verifyGuardianPin("9876"))
        assertTrue(store.verifyGuardianPin("1234") is PinVerification.Wrong)

        // O código antigo deixa de valer quando o PIN é trocado.
        assertTrue(store.verifyRecoveryCode(recoveryCode) is PinVerification.Wrong)
        assertEquals(PinVerification.Success, store.verifyRecoveryCode(newCode))
    }

    @Test
    fun codigoDeRecuperacaoErradoContaTentativa() {
        store.setGuardianPin("1234")

        assertTrue(store.verifyRecoveryCode("ZZZZ-ZZZZ") is PinVerification.Wrong)
        repeat(3) { store.verifyRecoveryCode("ZZZZ-ZZZZ") }

        assertTrue(store.verifyRecoveryCode("ZZZZ-ZZZZ") is PinVerification.Locked)
    }

    @Test
    fun hashAntigoSha256ContinuaValendoEMigra() {
        // Simula um aparelho atualizado: PIN gravado no formato antigo (SHA-256).
        val salt = ByteArray(16) { it.toByte() }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update("1234".toByteArray(Charsets.UTF_8))
        val legacyHash = android.util.Base64.encodeToString(digest.digest(), android.util.Base64.NO_WRAP)
        val legacySalt = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)

        preferences().edit()
            .putString("guardian_pin_hash", legacyHash)
            .putString("guardian_pin_salt", legacySalt)
            .commit()

        assertEquals(PinVerification.Success, store.verifyGuardianPin("1234"))
        // A migração para PBKDF2 acontece no primeiro acerto.
        assertEquals("pbkdf2v1", preferences().getString("guardian_pin_kdf", null))
        assertEquals(PinVerification.Success, store.verifyGuardianPin("1234"))
    }

    @Test
    fun removerPinApagaOCodigoDeRecuperacao() {
        store.setGuardianPin("1234")
        assertTrue(store.recoveryConfigured)

        assertEquals(PinVerification.Success, store.removeGuardianPin("1234"))

        assertEquals(false, store.recoveryConfigured)
        assertEquals(PinVerification.NotConfigured, store.verifyRecoveryCode("ABCD-EFGH"))
    }

    private fun preferences() =
        context.getSharedPreferences("senda_protection", Context.MODE_PRIVATE)
}
