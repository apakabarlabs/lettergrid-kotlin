package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RepeatedLettersTests {
    @ParameterizedTest
    @ValueSource(ints = [3, 4, 5, 6, 7])
    fun aValidStaggeredLayoutIsNotRefusedByItsFallback(width: Int) {
        val words = "ABCDEFG".cells.take(width).map { "T$it" }
        val placements = words.mapIndexed { index, word -> Placement(word, Cell(index % 2, index), Direction.DOWN) }
        val letters = List(3) { MutableList(width) { "X" } }
        for (placement in placements) {
            for ((cell, letter) in placement.cells.zip(placement.word.cells)) letters[cell.row][cell.column] = letter
        }
        val reference = Board.validating(letters, placements)
        assertFalse(hasAddedRun(reference))
        val generator =
            BoardGenerator(
                columns = width,
                rows = 3,
                maxRows = 3,
                directions = listOf(Direction.DOWN),
                filler = LetterWeights(mapOf("X" to 1.0)),
                downWords = width..width,
            )
        assertFalse(hasAddedRun(generator.request(words).board(42uL)))
    }

    @Test
    fun anImpossibleAlphabetIsRefusedDuringPreparation() {
        val generator =
            BoardGenerator(
                columns = 4,
                rows = 4,
                maxRows = 4,
                directions = listOf(Direction.RIGHT),
                filler = LetterWeights(mapOf("X" to 1.0)),
            )
        val failure = assertFailsWith<BoardGenerator.Failure> { generator.request(listOf("AB")) }
        assertEquals(BoardGenerator.Failure.Unsettled(listOf("AB"), 4, 4), failure)
    }

    @ParameterizedTest
    @MethodSource("targetRuns")
    fun forbiddenTargetRunsAreARefillFailureRatherThanMissingGeometry(words: List<String>) {
        val generator =
            BoardGenerator(
                columns = 3,
                rows = 3,
                maxRows = 3,
                directions = listOf(Direction.DOWN),
                filler = LetterWeights(mapOf("X" to 1.0, "Y" to 1.0, "Z" to 1.0)),
            )
        val failure = assertFailsWith<BoardGenerator.Failure> { generator.request(words) }
        assertEquals(BoardGenerator.Failure.Unsettled(listOf("TAB", "TBA", "TAA"), 3, 3), failure)
    }

    @Serializable
    private data class Case(
        val name: String,
        val words: List<String>,
        val columns: Int,
        val rows: Int,
        val weights: Map<String, Double>,
        val turns: Int,
        val avoidsPairs: Boolean? = null,
    )

    @Test
    fun generatedBoardsNeverExtendAWordRunOrCreateARunAcrossWords() {
        for (item in Fixtures.decode("repeated-letters", ListSerializer(Case.serializer()))) {
            val rules = listOf(WordRule(Direction.entries, item.turns))
            val request =
                BoardGenerator(
                    columns = item.columns,
                    rows = item.rows,
                    maxRows = item.rows,
                    rules = rules,
                    filler = LetterWeights(item.weights),
                ).request(item.words)
            val signatures = mutableMapOf<String, Int>()
            for (seed in 100uL until 164uL) {
                val board = request.board(seed)
                val signature = board.letters.joinToString("/") { it.joinToString("") }
                signatures[signature] = (signatures[signature] ?: 0) + 1
                check(board, item, rules, seed)
            }
            val peak = signatures.values.maxOrNull() ?: 0
            assertTrue(signatures.size > 1, "${item.name}: ${signatures.size}/64 fields; peak $peak/64")
        }
    }

    private fun check(
        board: Board,
        item: Case,
        rules: List<WordRule>,
        seed: ULong,
    ) {
        assertFalse(hasAddedRun(board), "${item.name}, seed $seed")
        if (item.avoidsPairs == true) {
            for ((cell, next) in neighbourPairs(board)) assertNotEquals(board[cell], board[next], item.name)
        }
        for (word in item.words) assertEquals(1, board.occurrences(word, rules = rules))
    }

    private fun cells(board: Board): List<Cell> =
        (0 until board.rows).flatMap { row -> (0 until board.columns).map { Cell(row, it) } }

    private fun neighbourPairs(board: Board): List<Pair<Cell, Cell>> =
        cells(board).flatMap { cell ->
            Direction.entries
                .map { cell to Cell(cell.row + it.rowStep, cell.column + it.columnStep) }
                .filter { (_, next) -> board.contains(next) }
        }

    private fun hasAddedRun(board: Board): Boolean {
        val runs =
            cells(board).flatMap { start ->
                Direction.entries.map { direction -> (0 until 3).map { start.moved(direction, it) } }
            }
        return runs.any { cells ->
            cells.all(board::contains) &&
                cells.map { board[it] }.toSet().size == 1 &&
                board.placements.none { placement ->
                    placement.cells.size >= 3 &&
                        (0..(placement.cells.size - 3)).any { start ->
                            val wordCells = placement.cells.subList(start, start + 3)
                            wordCells == cells || wordCells.asReversed() == cells
                        }
                }
        }
    }

    companion object {
        @JvmStatic
        fun targetRuns(): List<List<String>> = listOf(listOf("TAB", "TBA", "TAA"), listOf("tab", "tba", "taa"))
    }
}
