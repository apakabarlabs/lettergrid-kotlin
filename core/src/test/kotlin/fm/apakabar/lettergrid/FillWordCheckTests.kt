package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FillWordCheckTests {
    @Test
    fun everyTrialMatchesAFullSearchAcrossBacktrackingAndRuleKinds() {
        val alphabet = "ABCDEFGHIJ".cells
        val large = alphabet.flatMap { a -> alphabet.flatMap { b -> alphabet.map { a + b + it } } }.toSet()
        val small = setOf("AXD", "DAQ", "DXA", "CAT", "DOG", "SUN", "A", "AAAAAAAAD")
        for ((columns, rows) in listOf(5 to 5, 7 to 6)) {
            val board = FillTestBoard.make(listOf("CAT", "DOG", "SUN"), columns, rows)
            for (words in listOf(small, large + small)) {
                val index = WordIndex(words)
                for (rules in listOf(
                    listOf(WordRule(listOf(Direction.RIGHT, Direction.DOWN), 0)),
                    listOf(WordRule(Direction.entries, 0)),
                    listOf(WordRule(listOf(Direction.RIGHT, Direction.DOWN), 1)),
                )) {
                    compare(board, index, rules)
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [111, 112, 149, 150])
    fun theIncrementalAndFullSearchBudgetBoundariesMatchTheOracle(rows: Int) {
        val letters = List(rows) { List(224) { "A" } }
        val board = Board(letters, emptyList())
        val index = WordIndex(setOf("AAAB"))
        val rules = listOf(WordRule(listOf(Direction.RIGHT), 0))
        val full = board.strayWordCount(index, rules, lessThan = 1)
        assertEquals(if (rows < 150) 0 else Int.MAX_VALUE, full)
        val check = FillWordCheck(board, index, rules, board.placementLetters)
        assertEquals(full == 0, check.isClean(Cell(rows - 1, 223), letters))
    }

    @Test
    fun aFixedConflictIsStillRejectedWhenTheChangedCellCannotTouchIt() {
        val board = FillTestBoard.make(listOf("CAT", "DOG", "SUN"), rowSpacing = 1)
        val letters = board.placementLetters.map { it.toMutableList() }
        val index = WordIndex(setOf("CDS"))
        val rules = listOf(WordRule(listOf(Direction.DOWN), 0))
        val check = FillWordCheck(board, index, rules, letters.map { it.toList() })
        val changed = Cell(4, 4)
        letters[changed.row][changed.column] = "X"
        assertFalse(check.isClean(changed, letters))
        assertEquals(1, board.strayWordCount(index, rules, letters = { letters[it.row][it.column] }))
    }

    private fun compare(
        board: Board,
        index: WordIndex,
        rules: List<WordRule>,
    ) {
        val letters = board.placementLetters.map { it.toMutableList() }
        val check = FillWordCheck(board, index, rules, letters.map { it.toList() })
        val empty =
            (0 until board.rows).flatMap { row ->
                (0 until board.columns).mapNotNull { column ->
                    if (letters[row][column] ==
                        null
                    ) {
                        Cell(row, column)
                    } else {
                        null
                    }
                }
            }
        val random = SeededRandom(37uL)
        var position = 0
        val candidates = "ADXQZ".cells
        repeat(300) {
            if (position == empty.size || (position > 0 && random.next() and 1uL == 0uL)) {
                position -= 1
                val cell = empty[position]
                letters[cell.row][cell.column] = null
            }
            val cell = empty[position]
            letters[cell.row][cell.column] = random.pick(candidates)
            val expected =
                board.strayWordCount(index, rules, lessThan = 1, letters = { letters[it.row][it.column] }) == 0
            assertEquals(expected, check.isClean(cell, letters))
            if (expected) position += 1 else letters[cell.row][cell.column] = null
        }
    }
}
