package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlternativeReadingTests {
    @Serializable
    data class Rule(
        val directions: List<Direction>,
        val turns: Int,
    ) {
        val value: WordRule get() = WordRule(directions, turns)
    }

    @Serializable
    data class Case(
        val name: String,
        val letters: List<String>,
        val word: String,
        val generated: List<List<Int>>,
        val rules: List<Rule>,
        val drawn: List<List<Int>>,
        val accepted: Boolean,
    ) {
        override fun toString(): String = name
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun aWordIsAcceptedAlongEveryPathTheOpenRulesAllow(example: Case) {
        val board =
            Board(
                example.letters.map { row -> row.map(Char::toString) },
                listOf(Placement(example.word, cells(example.generated))),
            )
        val round = Round(board, example.rules.map { it.value })
        val drawn = cells(example.drawn)
        round.touch(drawn.first())
        for (cell in drawn.drop(1)) round.drag(cell)
        assertEquals(example.accepted, round.selection == drawn, "selected")
        val placement = round.release()
        if (!example.accepted) {
            assertNull(placement)
            return
        }
        assertEquals(Placement(example.word, drawn), placement)
        assertEquals(listOf(Placement(example.word, drawn)), round.board.placements)
        assertEquals(Round.Phase.WON, round.phase)
    }

    companion object {
        @JvmStatic
        fun cases(): List<Case> = Fixtures.decode("alternative-readings", ListSerializer(Case.serializer()))

        private fun cells(pairs: List<List<Int>>): List<Cell> = pairs.map { Cell(it[0], it[1]) }
    }
}
