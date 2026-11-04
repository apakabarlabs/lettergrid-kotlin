package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CellCaseTests {
    @Test
    fun aLetterWithALongerCapitalKeepsItsCell() {
        assertEquals("ԵՐևԱՆ", "երևան".cellUppercased)
        assertEquals("STRAßE", "straße".cellUppercased)
        assertEquals("ՈՒ", "ու".cellUppercased)
    }

    @Test
    fun fillerKeepsALetterWithALongerCapital() {
        val filler = LetterWeights(mapOf("ե" to 1.0, "և" to 1.0, "ր" to 1.0))
        assertEquals(3, filler.letterCount)
        assertEquals(setOf("Ե", "և", "Ր"), filler.orderedLetters(SeededRandom(1uL)).toSet())
    }

    @Test
    fun aWordWithALongerCapitalTakesOneCellPerLetter() {
        val generator =
            BoardGenerator(
                columns = 6,
                rows = 6,
                directions = listOf(Direction.RIGHT, Direction.DOWN),
                filler = LetterWeights(mapOf("ա" to 1.0, "ն" to 1.0, "ս" to 1.0, "տ" to 1.0, "կ" to 1.0)),
            )
        val request = generator.request(listOf("երևան", "ևս"))
        val board = request.board(7uL)
        assertEquals(listOf("ԵՐևԱՆ", "ևՍ"), request.words)
        assertEquals(1, board.occurrences("ԵՐևԱՆ", along = listOf(Direction.RIGHT, Direction.DOWN)))
        assertEquals(listOf(2, 5), board.placements.map { it.cells.size }.sorted())
    }
}
