package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PathSearchTests {
    private val rules = (0..3).map { WordRule(Direction.entries, it) }

    @Test
    fun countingStrayWordsAgreesWithTheFullSearch() {
        val dictionary = WordIndex(setOf("AB", "ABC", "BAD", "CAB", "DAD", "ABBA", "CDDC"))
        val random = SeededRandom(11uL)
        val letters = listOf("A", "B", "C", "D")
        repeat(200) {
            val grid = List(4) { List(4) { letters[(random.next() % letters.size.toULong()).toInt()] } }
            val bare = Board(grid, emptyList())
            assertEquals(strayWords(bare, dictionary), bare.strayWordCount(dictionary, rules))
            val row = grid[0].joinToString("")
            val covered = Board(grid, listOf(Placement(row, Cell(0, 0), Direction.RIGHT)))
            assertEquals(strayWords(covered, dictionary), covered.strayWordCount(dictionary, rules))
        }
    }

    private fun strayWords(
        board: Board,
        dictionary: WordIndex,
    ): Int =
        PathSearch(board, rules)
            .readings(dictionary)
            .filter { !board.covers(it) }
            .map { it.word }
            .toSet()
            .size
}
