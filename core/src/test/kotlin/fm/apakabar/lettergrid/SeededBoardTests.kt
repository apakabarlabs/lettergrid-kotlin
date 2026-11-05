package fm.apakabar.lettergrid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SeededBoardTests {
    @Serializable
    private data class Case(
        val name: String,
        val columns: Int,
        val rows: Int,
        @SerialName("max_rows") val maxRows: Int? = null,
        val directions: List<Direction>,
        val turns: List<Int>? = null,
        @SerialName("first_rule_turns") val firstRuleTurns: Int? = null,
        @SerialName("down_words") val downWords: List<Int>,
        val symbols: List<String>,
        val words: List<String>,
        val letters: String,
        val placements: Map<String, String>,
    )

    @Serializable
    private data class Fixture(
        val seed: String,
        val filler: Map<String, Double>,
        val cases: List<Case>,
    )

    @Test
    fun aSeedKeepsItsRecordedBoard() {
        val fixture = Fixtures.decode("seeded-boards", Fixture.serializer())
        val filler = LetterWeights(fixture.filler)
        for (test in fixture.cases) {
            val board = request(test, filler).board(fixture.seed.toULong())
            assertEquals(test.letters, board.letters.joinToString("/") { it.joinToString("") }, test.name)
            assertEquals(test.placements, placements(board), test.name)
        }
    }

    private fun request(
        test: Case,
        filler: LetterWeights,
    ): BoardRequest {
        val downWords = test.downWords.first()..test.downWords.last()
        val symbols = test.symbols.map { it.cells.first() }.toSet()
        val turns =
            test.turns
                ?: return BoardGenerator(
                    size = BoardSize(test.columns, test.rows, test.maxRows),
                    rules = listOf(WordRule.straight(test.directions)),
                    filler = Filler(filler, conditionalCharacters = symbols),
                    limits = PlacementLimits(downWords = downWords),
                ).request(test.words)
        val rules = turns.map { WordRule(test.directions, it) }
        val first = rules.first { it.turns == test.firstRuleTurns }
        return BoardGenerator(
            size = BoardSize(test.columns, test.rows, test.maxRows),
            rules = rules,
            filler = Filler(filler, conditionalCharacters = symbols),
            limits = PlacementLimits(downWords = downWords),
        ).request(test.words, firstRule = first)
    }

    private fun placements(board: Board): Map<String, String> =
        board.placements.associate { placement ->
            placement.word to placement.cells.joinToString(";") { "${it.row},${it.column}" }
        }
}
