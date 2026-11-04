package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BoardRequestTests {
    @Test
    fun anEmptyTargetListCannotBecomeAPlayableRequest() {
        val generator =
            BoardGenerator(
                columns = 5,
                rows = 5,
                directions = listOf(Direction.RIGHT),
                filler =
                    LetterWeights(
                        mapOf("x" to 1.0),
                    ),
            )
        val failure = assertFailsWith<BoardGenerator.Failure> { generator.request(emptyList()) }
        assertEquals(BoardGenerator.Failure.NoRoom(emptyList(), 5, 5), failure)
    }

    @Test
    fun aPreparedRequestRetainsItsGenerationSettings() {
        val request =
            BoardGenerator(
                columns = 3,
                rows = 4,
                directions = listOf(Direction.DOWN),
                filler = LetterWeights(mapOf("x" to 1.0, "y" to 1.0, "z" to 1.0, "q" to 1.0)),
                downWords = 2..2,
            ).request(listOf("CAT", "DOG"))
        val board = request.board(42uL)
        assertEquals(3, board.columns)
        assertTrue(board.rows >= 4)
        assertEquals(listOf(Direction.DOWN, Direction.DOWN), board.placements.map { it.direction })
        assertEquals(request.words, board.placements.map { it.word })
        for (word in request.words) assertEquals(1, board.occurrences(word, along = listOf(Direction.DOWN)))
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1])
    fun invalidDimensionsAreRejectedBeforePreparingARequest(dimension: Int) {
        val filler = LetterWeights(mapOf("x" to 1.0))
        val generators =
            listOf(
                BoardGenerator(columns = dimension, rows = 5, directions = listOf(Direction.DOWN), filler = filler),
                BoardGenerator(columns = 5, rows = dimension, directions = listOf(Direction.RIGHT), filler = filler),
            )
        for (generator in generators) {
            val failure = assertFailsWith<BoardGenerator.Failure> { generator.request(listOf("CAT")) }
            assertEquals(BoardGenerator.Failure.InvalidConfiguration, failure)
        }
    }

    @Test
    fun invalidQuotasAndDirectionsAreRejectedBeforePreparingARequest() {
        val filler = LetterWeights(mapOf("x" to 1.0))
        val generators =
            listOf(
                BoardGenerator(columns = 5, rows = 5, directions = emptyList(), filler = filler),
                BoardGenerator(
                    columns = 5,
                    rows = 5,
                    directions = listOf(Direction.RIGHT),
                    filler = filler,
                    downWords =
                        -1..1,
                ),
            )
        for (generator in generators) {
            val failure = assertFailsWith<BoardGenerator.Failure> { generator.request(listOf("CAT")) }
            assertEquals(BoardGenerator.Failure.InvalidConfiguration, failure)
        }
    }
}
