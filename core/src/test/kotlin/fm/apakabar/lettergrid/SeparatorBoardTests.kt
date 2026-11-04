package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SeparatorBoardTests {
    @Serializable
    private data class Example(
        val name: String,
        val words: List<String>,
        val turns: Int,
        val bounded: Boolean,
        val columns: Int? = null,
        val rows: Int? = null,
        val rejects: Boolean? = null,
        val conditional: List<String>? = null,
        val weights: Map<String, Double>? = null,
    )

    @Test
    fun separatorsHaveDecoysAndRemainCellsOfTheFoundPath() {
        for (example in Fixtures.decode("separator-boards", ListSerializer(Example.serializer()))) check(example)
    }

    private fun check(example: Example) {
        val rule = WordRule(listOf(Direction.RIGHT, Direction.DOWN), example.turns)
        val conditional = example.conditional.orEmpty().mapTo(mutableSetOf()) { it.cellUppercased }
        val generator =
            BoardGenerator(
                columns = example.columns ?: 7,
                rows = example.rows ?: 7,
                maxRows = if (example.bounded) example.rows ?: 7 else null,
                rules = listOf(rule),
                filler = LetterWeights(example.weights ?: mapOf("x" to 1.0, "q" to 1.0)),
                conditionalCharacters = conditional,
            )
        if (example.rejects == true) {
            assertFailsWith<BoardGenerator.Failure> { generator.request(example.words) }
            return
        }
        val request = generator.request(example.words)
        val symbols = conditional + setOf(" ", "-", "'")
        val used = example.words.flatMap { it.cells }.mapTo(mutableSetOf()) { it.cellUppercased }
        val targets = used intersect symbols
        for (seed in 1uL..3uL) {
            val board = request.board(seed)
            checkCopies(board, symbols, targets, example)
            checkPaths(board, rule, example.name)
        }
    }

    private fun checkPaths(
        board: Board,
        rule: WordRule,
        name: String,
    ) {
        val round = Round(board, rules = listOf(rule))
        for (placement in board.placements) {
            assertEquals(placement.word, board.text(placement.cells))
            assertEquals(placement.word.cells.size, placement.cells.size)
            assertEquals(1, board.occurrences(placement.word, rules = listOf(rule)))
            round.touch(placement.cells[0])
            for (cell in placement.cells.drop(1)) round.drag(cell)
            assertEquals(placement, round.release(), "$name: ${placement.word}")
        }
    }

    private fun checkCopies(
        board: Board,
        symbols: Set<String>,
        targets: Set<String>,
        example: Example,
    ) {
        for (symbol in symbols) {
            val actual = board.letters.flatten().count { it == symbol }
            if (symbol in targets) {
                assertTrue(actual >= 2, "${example.name}: \"$symbol\", $actual")
            } else {
                assertEquals(0, actual, "${example.name}: unexpected \"$symbol\"")
            }
        }
    }
}
