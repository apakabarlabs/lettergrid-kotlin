package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlacementCheckTests {
    @Serializable
    private data class Rule(
        val directions: List<Direction>,
        val turns: Int,
    ) {
        val value: WordRule get() = WordRule(directions, turns)
    }

    @Serializable
    private data class Case(
        val name: String,
        val columns: Int,
        val rows: Int,
        val maxRows: Int? = null,
        val directions: List<Direction>? = null,
        val rules: List<Rule>? = null,
        val firstDirection: Direction? = null,
        val firstRule: Rule? = null,
        val leadingRules: List<Rule>? = null,
        val weights: Map<String, Double>,
        val downWords: List<Int>,
        val words: List<String>,
        val placement: String,
        val request: String,
    ) {
        fun generator(): BoardGenerator {
            val filler = LetterWeights(weights)
            val quota = downWords[0]..downWords[1]
            if (rules != null) {
                return BoardGenerator(columns, rows, maxRows, rules = rules.map { it.value }, filler = filler, downWords = quota)
            }
            return BoardGenerator(columns, rows, maxRows, directions = checkNotNull(directions), filler = filler, downWords = quota)
        }

        fun check(generator: BoardGenerator) {
            if (leadingRules != null) {
                generator.checkPlacement(words, leadingRules.map { it.value })
            } else {
                generator.checkPlacement(words, firstDirection, firstRule?.value)
            }
        }

        fun prepare(generator: BoardGenerator): BoardRequest =
            if (leadingRules != null) {
                generator.request(words, leadingRules.map { it.value })
            } else {
                generator.request(words, firstDirection, firstRule?.value)
            }

        fun failure(outcome: String): BoardGenerator.Failure =
            when (outcome) {
                "invalidConfiguration" -> BoardGenerator.Failure.InvalidConfiguration
                "unsettled" -> BoardGenerator.Failure.Unsettled(words.map { it.cellUppercased }, columns, maxRows ?: rows)
                else -> BoardGenerator.Failure.NoRoom(words.map { it.cellUppercased }, columns, rows)
            }
    }

    @Test
    fun placementAndCompletePreparationHaveSeparateGuarantees() {
        for (item in Fixtures.decode("placement-checks", ListSerializer(Case.serializer()))) {
            val generator = item.generator()
            if (item.placement == "placed") {
                assertTrue(item.request == "prepared" || item.request == "unsettled", item.name)
                item.check(generator)
            } else {
                val failure = assertFailsWith<BoardGenerator.Failure>(item.name) { item.check(generator) }
                assertEquals(item.failure(item.placement), failure, item.name)
            }
            if (item.request == "prepared") {
                val request = item.prepare(generator)
                for (seed in 0uL..2uL) {
                    assertEquals(item.words.map { it.cellUppercased }, request.board(seed).placements.map { it.word }, item.name)
                }
            } else {
                val failure = assertFailsWith<BoardGenerator.Failure>(item.name) { item.prepare(generator) }
                assertEquals(item.failure(item.request), failure, item.name)
            }
        }
    }
}
