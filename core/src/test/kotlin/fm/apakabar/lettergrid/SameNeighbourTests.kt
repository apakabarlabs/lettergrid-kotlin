package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SameNeighbourTests {
    private val weights = mapOf("a" to 8.0, "e" to 12.0, "t" to 9.0, "s" to 6.0, "u" to 4.0, "q" to 0.1)
    private val words = listOf("TUBA", "SEAT", "MUST")

    @Test
    fun theDefaultWeightLeavesBoardsAsTheyWere() {
        val directions = listOf(Direction.RIGHT, Direction.DOWN)
        val plain =
            BoardGenerator(
                columns = 7,
                rows = 7,
                directions = directions,
                filler = LetterWeights(weights),
                downWords =
                    1..2,
            )
        val explicit =
            BoardGenerator(
                columns = 7,
                rows = 7,
                directions = directions,
                filler = LetterWeights(weights, sameNeighbour = 1.0),
                downWords = 1..2,
            )
        assertEquals(plain.request(words).board(3uL), explicit.request(words).board(3uL))
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.0, -0.5, 1.5])
    fun aWeightOutsideZeroToOneIsRejected(sameNeighbour: Double) {
        val failure = assertFailsWith<LetterWeights.Failure> { LetterWeights(mapOf("a" to 1.0), sameNeighbour) }
        assertEquals(LetterWeights.Failure.SameNeighbourOutsideFraction(sameNeighbour), failure)
    }
}
