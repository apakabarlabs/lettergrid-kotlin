package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HintCellsTests {
    private val board =
        Board(
            listOf("XOX".cells, "CAT".cells, "XRX".cells),
            listOf(Placement("CAT", Cell(1, 0), Direction.RIGHT), Placement("OAR", Cell(0, 1), Direction.DOWN)),
        )
    private val directions = listOf(WordRule.straight(listOf(Direction.RIGHT, Direction.DOWN)))

    @Test
    fun crossingHintsKeepPlacementOrderAndRepeatedCells() {
        val round = Round(board, directions, progress = RoundProgress(revealed = mapOf("CAT" to 2, "OAR" to 2)))
        assertEquals(listOf(Cell(1, 0), Cell(1, 1), Cell(0, 1), Cell(1, 1)), round.hintedCells)
    }

    @Test
    fun findingAWordRemovesItsHintsButKeepsTheCrossingWordHints() {
        val round =
            Round(
                board,
                directions,
                progress =
                    RoundProgress(
                        found = listOf("CAT"),
                        revealed =
                            mapOf(
                                "CAT" to 2,
                                "OAR" to 2,
                            ),
                    ),
            )
        assertEquals(listOf(Cell(0, 1), Cell(1, 1)), round.hintedCells)
    }

    @Test
    fun aLongWordRestoresTheSameShuffledHintOrder() {
        val word = "A".repeat(256)
        val placement = Placement(word, Cell(0, 0), Direction.RIGHT)
        val board = Board(listOf(word.cells), listOf(placement))
        val round =
            Round(
                board,
                listOf(WordRule.straight(listOf(Direction.RIGHT))),
                RoundOptions(hintOrder = HintOrder.ANY_BUT_FIRST),
            )
        repeat(word.length - 1) { round.reveal() }
        val before = round.hintedCells
        val restored =
            Round.restoring(
                round.progress,
                board,
                rules = listOf(WordRule.straight(listOf(Direction.RIGHT))),
                options = RoundOptions(hintOrder = HintOrder.ANY_BUT_FIRST),
            )
        assertEquals(255, before.size)
        assertEquals(placement.cells.drop(1).toSet(), before.toSet())
        assertEquals(before, restored.hintedCells)
        assertEquals(before, round.hintedCells)
        assertEquals(listOf<String?>(null) + List(255) { "A" }, restored.shownLetters(placement))
        assertNull(restored.hintTarget)
    }
}
