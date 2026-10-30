package com.senda.app.domain.security

import kotlin.math.abs

/**
 * Detecta mudança manual do relógio comparando o relógio de parede com o tempo
 * monotônico (`elapsedRealtime`).
 *
 * A âncora guarda também o **offset de fuso** do momento. Sem isso, viajar para
 * outro fuso (ou uma correção automática de horário) parecia fraude: o relógio
 * de parede pulava horas enquanto o monotônico não mudava. Agora a diferença de
 * fuso é descontada; só o que sobra depois disso conta como adulteração.
 */
object ClockDriftDetector {

    const val TAMPER_THRESHOLD_MS = 120_000L
    const val ANCHOR_REFRESH_MS = 60_000L

    /**
     * Após um reboot o tempo monotônico recomeça, então só é possível comparar
     * relógio de parede. A tolerância maior cobre uma sincronização de horário
     * (NTP) depois de o aparelho ficar desligado por muito tempo.
     */
    const val BOOT_BACKWARD_TOLERANCE_MS = 5 * 60_000L

    data class Anchor(
        val wallMillis: Long,
        val elapsedRealtime: Long,
        val bootCount: Int,
        val zoneOffsetSeconds: Int = 0
    )

    data class Evaluation(
        val tampered: Boolean,
        val nextAnchor: Anchor?,
        val zoneChanged: Boolean = false
    )

    fun evaluate(
        anchor: Anchor?,
        nowWallMillis: Long,
        nowElapsedRealtime: Long,
        bootCount: Int,
        nowZoneOffsetSeconds: Int = 0
    ): Evaluation {
        if (anchor == null) {
            return Evaluation(
                tampered = false,
                nextAnchor = Anchor(nowWallMillis, nowElapsedRealtime, bootCount, nowZoneOffsetSeconds)
            )
        }

        val zoneDeltaMs = (nowZoneOffsetSeconds - anchor.zoneOffsetSeconds) * 1_000L
        val zoneChanged = zoneDeltaMs != 0L

        if (anchor.bootCount != bootCount) {
            // Depois de reiniciar não há tempo monotônico comparável: só dá para
            // checar se o relógio voltou muito além do que o fuso explica.
            val expectedWall = anchor.wallMillis + zoneDeltaMs
            return Evaluation(
                tampered = nowWallMillis + BOOT_BACKWARD_TOLERANCE_MS < expectedWall,
                nextAnchor = Anchor(nowWallMillis, nowElapsedRealtime, bootCount, nowZoneOffsetSeconds),
                zoneChanged = zoneChanged
            )
        }

        val wallDelta = nowWallMillis - anchor.wallMillis
        val elapsedDelta = nowElapsedRealtime - anchor.elapsedRealtime
        val drift = wallDelta - elapsedDelta - zoneDeltaMs

        if (abs(drift) >= TAMPER_THRESHOLD_MS) {
            return Evaluation(
                tampered = true,
                nextAnchor = Anchor(nowWallMillis, nowElapsedRealtime, bootCount, nowZoneOffsetSeconds),
                zoneChanged = zoneChanged
            )
        }

        // Mudança de fuso precisa ser persistida na hora; se a âncora ficasse
        // com o offset antigo, a próxima checagem veria a mesma diferença de novo.
        val shouldRefresh = elapsedDelta >= ANCHOR_REFRESH_MS || zoneChanged
        return Evaluation(
            tampered = false,
            nextAnchor = if (shouldRefresh) {
                Anchor(nowWallMillis, nowElapsedRealtime, bootCount, nowZoneOffsetSeconds)
            } else {
                null
            },
            zoneChanged = zoneChanged
        )
    }
}
