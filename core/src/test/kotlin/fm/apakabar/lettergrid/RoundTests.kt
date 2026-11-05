package fm.apakabar.lettergrid

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundTests {
    private val board =
        Board(
            listOf("HOUSEX".cells, "QDOORZ".cells, "XXXXXX".cells),
            listOf(Placement("HOUSE", Cell(0, 0), Direction.RIGHT), Placement("DOOR", Cell(1, 1), Direction.RIGHT)),
        )

    private fun revealAll(round: Round) {
        while (round.reveal() != null) continue
    }

    @Test
    fun aDragHalfwayBetweenTwoStraightDirectionsFollowsTheOneListedFirst() {
        val square =
            Board(
                listOf("CAT".cells, "XXX".cells, "XXX".cells),
                listOf(Placement("CAT", Cell(0, 0), Direction.RIGHT)),
            )
        for ((ways, chosen) in listOf(
            listOf(Direction.DOWN, Direction.RIGHT) to Direction.DOWN,
            listOf(Direction.RIGHT, Direction.DOWN) to Direction.RIGHT,
        )) {
            val round = Round(square, listOf(WordRule.straight(ways)))
            round.touch(Cell(0, 0))
            round.drag(Cell(1, 1))
            assertEquals(listOf(Cell(0, 0), Cell(chosen.rowStep, chosen.columnStep)), round.selection, "$ways")
        }
    }

    @Test
    fun aStraightDragPastTheEdgeStopsAtTheLastCell() {
        val round = Round(board, listOf(WordRule.straight(listOf(Direction.RIGHT, Direction.DOWN))))
        round.touch(Cell(0, 2))
        round.drag(Cell(0, 9))
        assertEquals((2..5).map { Cell(0, it) }, round.selection)
    }

    @Test
    fun aHintFromTheStartOpensTheLettersInOrderAndKeepsTheLastHidden() {
        val round = Round(board, listOf(WordRule.straight(listOf(Direction.RIGHT, Direction.DOWN))))
        val house = board.placements[0]
        revealAll(round)
        assertEquals(listOf("H", "O", "U", "S", null), round.shownLetters(house))
        assertNull(round.hintTarget)
    }

    @Test
    fun aHintAnywhereButTheFirstLetterOpensEveryOtherLetterAndNeverTheFirst() {
        val round =
            Round(
                board,
                listOf(WordRule.straight(listOf(Direction.RIGHT, Direction.DOWN))),
                RoundOptions(hintOrder = HintOrder.ANY_BUT_FIRST),
            )
        val house = board.placements[0]
        revealAll(round)
        assertEquals(listOf(null, "O", "U", "S", "E"), round.shownLetters(house))
        assertEquals(4, round.revealed[house])
    }

    @Test
    fun theLettersAHintOpensAreTheSameEveryTime() {
        val first =
            Round(
                board,
                listOf(WordRule.straight(listOf(Direction.RIGHT))),
                RoundOptions(hintOrder = HintOrder.ANY_BUT_FIRST),
            )
        val second =
            Round(
                board,
                listOf(WordRule.straight(listOf(Direction.RIGHT))),
                RoundOptions(hintOrder = HintOrder.ANY_BUT_FIRST),
            )
        first.reveal()
        second.reveal()
        assertEquals(second.hintedCells, first.hintedCells)
        assertEquals(1, first.hintedCells.size)
        assertNotEquals(listOf(board.placements[0].cells[0]), first.hintedCells)
    }

    @Test
    fun aFoundWordShowsEveryLetterAndNoLongerMarksItsHints() {
        val round = Round(board, listOf(WordRule.straight(listOf(Direction.RIGHT))))
        val door = board.placements[1]
        repeat(3) { round.reveal() }
        round.touch(door.cells[0])
        round.drag(door.cells[3])
        round.release()
        assertEquals(listOf("D", "O", "O", "R"), round.shownLetters(door))
        assertTrue(round.hintedCells.all { it !in door.cells })
    }

    @Test
    fun theIntroducedWordIsFoundOnceItsLastLetterIsShown() {
        val round = Round(board, listOf(WordRule.straight(listOf(Direction.RIGHT))), RoundOptions(introduces = true))
        while (round.phase == Round.Phase.INTRODUCING) round.introduceNextLetter()
        assertEquals(setOf(board.placements[0]), round.found)
        assertEquals(Round.Phase.PLAYING, round.phase)
    }

    @Test
    fun theCountdownIntroducesTheFirstWordLetterByLetter() =
        runTest {
            val round =
                Round(board, listOf(WordRule.straight(listOf(Direction.RIGHT))), RoundOptions(introduces = true))
            var letters = 0
            Countdown { yield() }.run(
                round,
                secondsPerLetter = 0.1,
                introduce = {
                    letters += 1
                    round.introduceNextLetter()
                },
                tick = {},
            )
            assertEquals(6, letters)
            assertEquals(Round.Phase.PLAYING, round.phase)
        }
}
