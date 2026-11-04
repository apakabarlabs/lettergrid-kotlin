package fm.apakabar.lettergrid

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoneReadingTests {
    private val groups =
        listOf(
            listOf(Direction.RIGHT, Direction.DOWN),
            listOf(Direction.DOWN_RIGHT, Direction.DOWN_LEFT),
            listOf(Direction.LEFT, Direction.UP),
            listOf(Direction.UP_LEFT, Direction.UP_RIGHT),
        )

    private fun rules(turns: Int) = (0..turns).flatMap { count -> groups.map { WordRule(it, count) } }

    private fun shapes(
        length: Int,
        direction: Direction,
        turns: Int,
    ) = PathShapes(
        columns = 7,
        length = length,
        rule = WordRule(listOf(direction), turns),
        maximumShapes = Int.MAX_VALUE,
        maximumRows = 7,
        visitedLimit = null,
        randomized = false,
    ).cells()

    private fun word(
        length: Int,
        random: SeededRandom,
    ) = (0 until length).joinToString("") { if (random.nextInt(below = 3) == 0) "B" else "A" }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun aLonePlacementReadsOnceExactlyWhenTheBoardSearchSaysSo(turns: Int) {
        val rules = rules(turns)
        val random = SeededRandom(turns.toULong())
        var compared = 0
        for (length in 4..9) {
            for (rule in rules.filter { it.turns == turns }) {
                for (direction in rule.startDirections) {
                    for (shape in shapes(length, direction, turns)) {
                        val word = word(length, random)
                        val placement = Placement(word, shape)
                        val expected = board(placement).hasSingleOccurrence(word, rules)
                        assertEquals(expected, placement.readsOnceAlone(rules), "$word along $shape with $turns turns")
                        compared += 1
                    }
                }
            }
        }
        assertTrue(compared >= Direction.entries.size * (4..7).count())
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun aLonePlacementHoldsAnotherWordExactlyWhenTheBoardSearchFindsIt(turns: Int) {
        val rules = rules(turns)
        val random = SeededRandom(turns.toULong() + 10uL)
        var held = 0
        var compared = 0
        for (length in 4..9) {
            for (direction in Direction.entries) {
                for (shape in shapes(length, direction, turns)) {
                    val word = word(length, random)
                    val other = word(random.nextInt(below = 3) + 2, random)
                    val placement = Placement(word, shape)
                    val expected = PathSearch(board(placement), rules).readings(WordIndex(setOf(other))).isNotEmpty()
                    assertEquals(
                        expected,
                        placement.holdsReading(other, rules),
                        "$other in $word along $shape with $turns turns",
                    )
                    if (expected) held += 1
                    compared += 1
                }
            }
        }
        assertTrue(held in 1 until compared)
    }

    private fun board(placement: Placement): Board {
        val letters = List(7) { MutableList(7) { "." } }
        for ((cell, letter) in placement.cells.zip(placement.word.cells)) letters[cell.row][cell.column] = letter
        return Board(letters, listOf(placement))
    }
}
