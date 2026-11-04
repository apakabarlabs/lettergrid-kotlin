package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReverseDirectionTests {
    private val reverses = listOf(Direction.LEFT, Direction.UP, Direction.UP_LEFT, Direction.UP_RIGHT)
    private val filler = LetterWeights(mapOf("x" to 1.0, "q" to 1.0, "z" to 1.0))

    @Test
    fun eachReverseDirectionStepsOppositeToItsForwardOne() {
        val pairs =
            listOf(
                Direction.RIGHT to Direction.LEFT,
                Direction.DOWN to Direction.UP,
                Direction.DOWN_RIGHT to Direction.UP_LEFT,
                Direction.DOWN_LEFT to Direction.UP_RIGHT,
            )
        for ((forward, reverse) in pairs) {
            assertEquals(-forward.rowStep, reverse.rowStep)
            assertEquals(-forward.columnStep, reverse.columnStep)
            assertEquals(reverse, forward.opposite)
            assertEquals(forward, reverse.opposite)
        }
    }

    @Test
    fun aBoardCanUseEachReverseDirectionReadingEachWordOnce() {
        val words = listOf("CAT", "DOG", "SUN")
        for (direction in reverses) {
            val generator = BoardGenerator(columns = 7, rows = 7, directions = listOf(direction), filler = filler, downWords = 0..3)
            val board = generator.board(words, 8uL)
            assertTrue(board.placements.all { it.direction == direction }, "$direction")
            for (placement in board.placements) {
                assertEquals(placement.word, board.text(placement.cells))
                assertEquals(1, board.occurrences(placement.word, along = listOf(direction)))
            }
        }
    }

    @Test
    fun wordsRunningUpCountTowardTheVerticalQuota() {
        val generator =
            BoardGenerator(columns = 7, rows = 7, directions = listOf(Direction.RIGHT, Direction.UP), filler = filler, downWords = 1..1)
        for (seed in 1uL..20uL) {
            val board = generator.board(listOf("CAT", "DOG", "SUN"), seed)
            assertEquals(1, board.placements.count { it.direction == Direction.UP })
        }
    }

    @Test
    fun aWordThatReadsTheSameBackwardsStandsOnceWhenBothWaysAreOn() {
        val generator =
            BoardGenerator(
                columns = 7,
                rows = 7,
                directions = listOf(Direction.RIGHT, Direction.LEFT, Direction.DOWN, Direction.UP),
                filler = filler,
                downWords = 0..3,
            )
        for (seed in 1uL..20uL) {
            val board = generator.board(listOf("LEVEL", "CAT", "SUN"), seed)
            for (word in listOf("LEVEL", "CAT", "SUN")) assertEquals(1, board.occurrences(word, along = generator.directions))
        }
    }

    @Test
    fun aWordThatReadsTheSameBackwardsIsFoundFromEitherEnd() {
        val level = Placement("LEVEL", Cell(0, 0), Direction.RIGHT)
        val board = Board(listOf("LEVELX".cells, "XXXXXX".cells), listOf(level))
        for ((start, end) in listOf(0 to 4, 4 to 0)) {
            val round = Round(board, listOf(Direction.RIGHT, Direction.LEFT))
            round.touch(Cell(0, start))
            round.drag(Cell(0, end))
            assertEquals(level, round.release())
        }
    }

    @Test
    fun aWordThatReadsDifferentlyBackwardsIsNotFoundFromItsEnd() {
        val cat = Placement("CAT", Cell(0, 0), Direction.RIGHT)
        val board = Board(listOf("CATX".cells, "XXXX".cells), listOf(cat))
        val round = Round(board, listOf(Direction.RIGHT, Direction.LEFT))
        round.touch(Cell(0, 2))
        round.drag(Cell(0, 0))
        assertNull(round.release())
    }

    @Test
    fun aWordWhoseReverseLiesInsideAnotherIsRejectedWhenBothWaysAreOn() {
        val generator =
            BoardGenerator(
                columns = 7,
                rows = 7,
                directions = listOf(Direction.DOWN, Direction.UP),
                filler = LetterWeights(mapOf("x" to 1.0)),
                downWords = 0..2,
            )
        val failure = assertFailsWith<BoardGenerator.Failure> { generator.request(listOf("TAC", "CATS")) }
        assertEquals(BoardGenerator.Failure.NoRoom(listOf("TAC", "CATS"), 7, 7), failure)
    }

    @Test
    fun aDragTowardTheStartOfTheRowSelectsLeftwards() {
        val board = Board(listOf("TACX".cells, "XXXX".cells), emptyList())
        val cells = board.selection(Cell(0, 2), Cell(0, 0), listOf(Direction.RIGHT, Direction.LEFT))
        assertEquals("CAT", board.text(cells))
    }

    @Test
    fun aDragUpAndLeftSelectsTheReverseDiagonal() {
        val board = Board(listOf("TXX".cells, "XAX".cells, "XXC".cells), emptyList())
        val cells = board.selection(Cell(2, 2), Cell(0, 0), listOf(Direction.RIGHT, Direction.DOWN, Direction.UP_LEFT))
        assertEquals("CAT", board.text(cells))
    }
}
