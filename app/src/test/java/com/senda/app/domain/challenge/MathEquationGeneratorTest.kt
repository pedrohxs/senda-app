package com.senda.app.domain.challenge

import com.senda.app.data.local.entity.MathDifficulty
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MathEquationGeneratorTest {

    @Test
    fun equacoesFaceisTemRespostaNaoNegativa() {
        val random = Random(42)
        repeat(100) {
            val equation = MathEquationGenerator.generate(MathDifficulty.EASY, random)
            assertTrue(equation.answer >= 0)
            assertTrue(equation.text.contains("+") || equation.text.contains("-"))
        }
    }

    @Test
    fun equacoesMediasUsamMultiplicacaoEParenteses() {
        val random = Random(7)
        val equations = List(200) {
            MathEquationGenerator.generate(MathDifficulty.MEDIUM, random)
        }
        assertTrue(equations.all { it.answer >= 0 })
        assertTrue(equations.any { it.text.contains("*") })
        assertTrue(equations.any { it.text.contains("(") })
    }

    @Test
    fun equacoesDificeisTemMaisOperacoes() {
        val random = Random(11)
        val equations = List(200) {
            MathEquationGenerator.generate(MathDifficulty.HARD, random)
        }
        assertTrue(equations.all { it.answer >= 0 })
        assertTrue(equations.any { it.text.count { char -> char == '(' } >= 2 })
        assertTrue(equations.any { it.text.count { char -> char == '*' } >= 2 })
    }
}
