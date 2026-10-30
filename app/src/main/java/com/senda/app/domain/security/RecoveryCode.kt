package com.senda.app.domain.security

import java.security.SecureRandom
import kotlin.random.Random

/**
 * Código de recuperação do PIN do guardião.
 *
 * Sem ele, esquecer o PIN significa perder a possibilidade de editar as regras
 * (só reinstalando). O código é mostrado uma única vez, quando o PIN é criado,
 * e fica guardado apenas como hash.
 */
object RecoveryCode {

    /** Sem 0/O e 1/I para não confundir na hora de anotar. */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    const val LENGTH = 8

    /** Gerador para uso real (criptograficamente seguro). */
    fun generateSecure(random: SecureRandom = SecureRandom()): String =
        build { bound -> random.nextInt(bound) }

    /** Gerador determinístico para testes. */
    fun generate(random: Random): String = build { bound -> random.nextInt(bound) }

    /** Remove espaços, hífens e diferenças de caixa. */
    fun normalize(input: String): String = input
        .uppercase()
        .filter { it in ALPHABET }

    /** Formato para exibir e anotar: `ABCD-EFGH`. */
    fun format(raw: String): String =
        normalize(raw).chunked(LENGTH / 2).joinToString("-")

    fun isWellFormed(input: String): Boolean = normalize(input).length == LENGTH

    private fun build(nextInt: (Int) -> Int): String =
        buildString(LENGTH) {
            repeat(LENGTH) { append(ALPHABET[nextInt(ALPHABET.length)]) }
        }
}
