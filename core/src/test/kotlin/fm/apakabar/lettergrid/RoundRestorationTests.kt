package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RoundRestorationTests {
    private val board =
        Board(
            listOf("CAT".cells, "DOG".cells),
            listOf(Placement("CAT", Cell(0, 0), Direction.RIGHT), Placement("DOG", Cell(1, 0), Direction.RIGHT)),
        )
    private val right = listOf(WordRule.straight(listOf(Direction.RIGHT)))

    @ParameterizedTest
    @ValueSource(ints = [-1, 3, Int.MAX_VALUE])
    fun invalidRevealCountsAreReportedAtRestoration(count: Int) {
        val progress = RoundProgress(revealed = mapOf("CAT" to count))
        val failure = assertFailsWith<RoundProgress.Failure> { Round.restoring(progress, board, rules = right) }
        assertEquals(RoundProgress.Failure.InvalidRevealCount("CAT", count), failure)
        assertEquals(count, progress.revealed["CAT"])
    }

    @Test
    fun unknownAndRepeatedFoundWordsAreReported() {
        assertEquals(
            RoundProgress.Failure.UnknownWord("SUN"),
            assertFailsWith<RoundProgress.Failure> { RoundProgress(found = listOf("SUN")).validate(board) },
        )
        assertEquals(
            RoundProgress.Failure.DuplicateFoundWord("CAT"),
            assertFailsWith<RoundProgress.Failure> { RoundProgress(found = listOf("CAT", "CAT")).validate(board) },
        )
        assertEquals(
            RoundProgress.Failure.UnknownWord("SUN"),
            assertFailsWith<RoundProgress.Failure> { RoundProgress(revealed = mapOf("SUN" to 1)).validate(board) },
        )
    }

    @Test
    fun negativeSecondsAreReported() {
        val failure =
            assertFailsWith<RoundProgress.Failure> {
                Round.restoring(
                    RoundProgress(secondsLeft = -1),
                    board,
                    rules = right,
                )
            }
        assertEquals(RoundProgress.Failure.NegativeSeconds(-1), failure)
    }

    @Test
    fun aRoundMustAllowEveryWordToBeSelected() {
        val failure =
            assertFailsWith<Round.Failure> {
                Round.restoring(
                    RoundProgress(),
                    board,
                    rules = listOf(WordRule.straight(listOf(Direction.DOWN))),
                )
            }
        assertEquals(Round.Failure.UnselectableWord("CAT"), failure)
    }

    @Test
    fun anEmptyBoardIsAlreadyComplete() {
        val round = Round(Board(emptyList(), emptyList()), emptyList())
        assertEquals(Round.Phase.WON, round.phase)
    }

    @Test
    fun validProgressRoundTripsWithoutReplayingAnIntroduction() {
        val progress = RoundProgress(found = listOf("CAT"), revealed = mapOf("DOG" to 1), secondsLeft = 7)
        val round =
            Round.restoring(
                progress,
                board,
                rules = right,
                options = RoundOptions(seconds = 20, introduces = true),
            )
        assertEquals(progress, round.progress)
        assertEquals(Round.Phase.PLAYING, round.phase)
        assertEquals(listOf("D", null, null), round.shownLetters(board.placements[1]))
    }

    @Test
    fun aRestoredExpiredClockCannotPlayBeforeTheFirstTick() {
        val round =
            Round.restoring(
                RoundProgress(secondsLeft = 0),
                board,
                rules = right,
                options = RoundOptions(seconds = 20, introduces = true),
            )
        round.touch(Cell(0, 0))
        round.drag(Cell(0, 2))
        assertEquals(Round.Phase.LOST, round.phase)
        assertNull(round.release())
    }

    @Test
    fun externallyOwnedClockDoesNotStartARoundCountdown() {
        val progress = RoundProgress(found = listOf("CAT"), secondsLeft = 0)
        val round = Round.restoring(progress, board, rules = right)
        assertEquals(Round.Phase.PLAYING, round.phase)
        assertNull(round.secondsLeft)
    }
}
