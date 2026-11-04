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
    fun aHintFromTheStartOpensTheLettersInOrderAndKeepsTheLastHidden() {
        val round = Round(board, listOf(Direction.RIGHT, Direction.DOWN))
        val house = board.placements[0]
        revealAll(round)
        assertEquals(listOf("H", "O", "U", "S", null), round.shownLetters(house))
        assertNull(round.hintTarget)
    }

    @Test
    fun aHintAnywhereButTheFirstLetterOpensEveryOtherLetterAndNeverTheFirst() {
        val round = Round(board, listOf(Direction.RIGHT, Direction.DOWN), hintOrder = HintOrder.ANY_BUT_FIRST)
        val house = board.placements[0]
        revealAll(round)
        assertEquals(listOf(null, "O", "U", "S", "E"), round.shownLetters(house))
        assertEquals(4, round.revealed[house])
    }

    @Test
    fun theLettersAHintOpensAreTheSameEveryTime() {
        val first = Round(board, listOf(Direction.RIGHT), hintOrder = HintOrder.ANY_BUT_FIRST)
        val second = Round(board, listOf(Direction.RIGHT), hintOrder = HintOrder.ANY_BUT_FIRST)
        first.reveal()
        second.reveal()
        assertEquals(second.hintedCells, first.hintedCells)
        assertEquals(1, first.hintedCells.size)
        assertNotEquals(listOf(board.placements[0].cells[0]), first.hintedCells)
    }

    @Test
    fun aFoundWordShowsEveryLetterAndNoLongerMarksItsHints() {
        val round = Round(board, listOf(Direction.RIGHT))
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
        val round = Round(board, listOf(Direction.RIGHT), introduces = true)
        while (round.phase == Round.Phase.INTRODUCING) round.introduceNextLetter()
        assertEquals(setOf(board.placements[0]), round.found)
        assertEquals(Round.Phase.PLAYING, round.phase)
    }

    @Test
    fun theCountdownIntroducesTheFirstWordLetterByLetter() =
        runTest {
            val round = Round(board, listOf(Direction.RIGHT), introduces = true)
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
