package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutTests {
    private val directions = listOf(Direction.RIGHT, Direction.DOWN)
    private val generator =
        BoardGenerator(
            columns = 5,
            rows = 5,
            directions = directions,
            filler = LetterWeights(mapOf("x" to 1.0, "y" to 1.0, "z" to 1.0)),
            downWords = 1..1,
        )

    @Test
    fun crossingWordsStayCrossedOnTheCompletedBoard() {
        val words = listOf("CAT", "TAP", "DOG")
        val board = generator.board(words, 3uL)
        val cells = board.placements.flatMap { it.cells }
        assertTrue(cells.toSet().size < cells.size)
        assertTrue(board.rows == 5 && board.columns == 5)
        assertEquals(words, board.placements.map { it.word })
        for (word in words) assertEquals(1, board.occurrences(word, along = directions))
    }

    @Test
    fun everySeedOfCatTapDogSharesLettersWithoutExtraRows() {
        val request = generator.request(listOf("CAT", "TAP", "DOG"))
        for (seed in 1uL..100uL) {
            val board = request.board(seed)
            val cells = board.placements.flatMap { it.cells }
            assertEquals(5, board.rows)
            assertTrue(cells.toSet().size < cells.size, "seed $seed")
            assertEquals(listOf("CAT", "TAP", "DOG"), board.placements.map { it.word })
            assertEquals(1, board.placements.count { it.direction == Direction.DOWN })
        }
    }
}
