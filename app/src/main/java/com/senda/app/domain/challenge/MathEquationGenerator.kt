package com.senda.app.domain.challenge

import com.senda.app.data.local.entity.MathDifficulty
import kotlin.random.Random

data class MathEquation(
    val text: String,
    val answer: Int
)

object MathEquationGenerator {

    fun generate(
        difficulty: MathDifficulty,
        random: Random = Random.Default
    ): MathEquation = when (difficulty) {
        MathDifficulty.EASY -> easy(random)
        MathDifficulty.MEDIUM -> medium(random)
        MathDifficulty.HARD -> hard(random)
    }

    private fun easy(random: Random): MathEquation {
        val first = random.nextInt(2, 31)
        val second = random.nextInt(2, 31)
        return if (random.nextBoolean()) {
            MathEquation(
                text = "$first + $second",
                answer = first + second
            )
        } else {
            val bigger = maxOf(first, second)
            val smaller = minOf(first, second)
            MathEquation(
                text = "$bigger - $smaller",
                answer = bigger - smaller
            )
        }
    }

    private fun medium(random: Random): MathEquation = when (random.nextInt(4)) {
        0 -> {
            val a = random.nextInt(2, 13)
            val b = random.nextInt(2, 10)
            val c = random.nextInt(10, 91)
            MathEquation(
                text = "($a * $b) + $c",
                answer = a * b + c
            )
        }

        1 -> {
            val a = random.nextInt(2, 16)
            val b = random.nextInt(2, 16)
            val c = random.nextInt(2, 7)
            MathEquation(
                text = "($a + $b) * $c",
                answer = (a + b) * c
            )
        }

        2 -> {
            val a = random.nextInt(6, 19)
            val b = random.nextInt(2, 10)
            val maxC = (a * b - 1).coerceAtLeast(2)
            val c = random.nextInt(2, maxC + 1)
            MathEquation(
                text = "$a * $b - $c",
                answer = a * b - c
            )
        }

        else -> {
            val a = random.nextInt(2, 10)
            val b = random.nextInt(2, 10)
            val c = random.nextInt(10, 61)
            MathEquation(
                text = "($a * $b) - $c",
                answer = a * b - c
            ).let { equation ->
                if (equation.answer >= 0) {
                    equation
                } else {
                    MathEquation(
                        text = "($a * $b) + $c",
                        answer = a * b + c
                    )
                }
            }
        }
    }

    private fun hard(random: Random): MathEquation = when (random.nextInt(4)) {
        0 -> {
            val a = random.nextInt(4, 16)
            val b = random.nextInt(4, 13)
            val c = random.nextInt(3, 13)
            val d = random.nextInt(3, 13)
            MathEquation(
                text = "($a * $b) + ($c * $d)",
                answer = a * b + c * d
            )
        }

        1 -> {
            val a = random.nextInt(3, 21)
            val b = random.nextInt(3, 21)
            val c = random.nextInt(6, 21)
            val d = random.nextInt(1, 6)
            val e = random.nextInt(2, 31)
            MathEquation(
                text = "($a + $b) * ($c - $d) + $e",
                answer = (a + b) * (c - d) + e
            )
        }

        2 -> {
            val a = random.nextInt(7, 21)
            val b = random.nextInt(3, 10)
            val c = random.nextInt(2, 11)
            val d = random.nextInt(2, 11)
            val result = a * b - c * d
            if (result >= 0) {
                MathEquation(
                    text = "$a * $b - ($c * $d)",
                    answer = result
                )
            } else {
                MathEquation(
                    text = "$a * $b + ($c * $d)",
                    answer = a * b + c * d
                )
            }
        }

        else -> {
            val a = random.nextInt(3, 21)
            val b = random.nextInt(3, 21)
            val c = random.nextInt(2, 8)
            val d = random.nextInt(2, 41)
            val result = (a + b) * c - d
            if (result >= 0) {
                MathEquation(
                    text = "(($a + $b) * $c) - $d",
                    answer = result
                )
            } else {
                MathEquation(
                    text = "(($a + $b) * $c) + $d",
                    answer = (a + b) * c + d
                )
            }
        }
    }
}
