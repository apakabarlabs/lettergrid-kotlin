package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RunFreeFillTests {
    @Test
    fun aPairPreferenceCannotRejectAnOtherwisePlayableField() {
        val board =
            Board.validating(
                listOf("XXXXXXXXXXXX".cells, "ABXXXXXXXXXX".cells),
                listOf(Placement("AB", Cell(1, 0), Direction.RIGHT)),
            )
        val filled =
            assertNotNull(
                RunFreeFill(
                    board,
                    LetterWeights(mapOf("X" to 1.0, "Y" to 1.0, "Z" to 1.0)),
                    listOf(WordRule(listOf(Direction.RIGHT))),
                    emptySet(),
                ).filled(1uL),
            )
        val runs = LetterRuns(filled.placements, filled.columns, filled.rows)
        assertFalse(runs.containsForbiddenRun(filled.letters))
        assertTrue(filled.hasAddedEqualPair)
    }

    @Test
    fun aConditionalCopyMovesAwayFromItsTargetLetter() {
        val original =
            Board.validating(listOf("AÉÉXX".cells), listOf(Placement("AÉ", Cell(0, 0), Direction.RIGHT)))
        val fill =
            RunFreeFill(
                original,
                LetterWeights(mapOf("X" to 1.0, "Y" to 1.0, "É" to 1.0)),
                listOf(WordRule(listOf(Direction.RIGHT))),
                setOf("É"),
            )
        for (seed in 100uL until 108uL) {
            val board = assertNotNull(fill.filled(seed))
            val row = board.letters[0]
            assertTrue(row.zipWithNext().all { (first, second) -> first != second }, "seed $seed")
            assertTrue("É" in row.drop(2), "seed $seed")
        }
    }
}
