package fm.apakabar.lettergrid

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CountdownTests {
    private class SleepFailure : Exception()

    private val board = Board(listOf("CAT".cells), listOf(Placement("CAT", Cell(0, 0), Direction.RIGHT)))
    private val right = listOf(Direction.RIGHT)

    @ParameterizedTest
    @ValueSource(
        doubles = [
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE,
            9.223372036854775807E18, Double.MIN_VALUE, 0.0, -1.0,
        ],
    )
    fun invalidIntervalsAreReportedBeforeSleeping(seconds: Double) =
        runTest {
            val round = Round(board, right, introduces = true)
            val failure =
                assertFailsWith<Countdown.Failure> {
                    Countdown { throw SleepFailure() }.run(round, seconds, introduce = {}, tick = {})
                }
            assertEquals(Countdown.Failure.InvalidInterval, failure)
        }

    @Test
    fun callbacksThatDoNotAdvanceTheRoundFailInsteadOfLoopingForever() =
        runTest {
            val introduction = Round(board, right, introduces = true)
            val playing = Round(board, right, seconds = 1)
            assertEquals(
                Countdown.Failure.NoIntroductionProgress,
                assertFailsWith<Countdown.Failure> { Countdown {}.run(introduction, 0.1, introduce = {}, tick = {}) },
            )
            assertEquals(
                Countdown.Failure.NoClockProgress,
                assertFailsWith<Countdown.Failure> { Countdown {}.run(playing, 0.1, introduce = {}, tick = {}) },
            )
        }

    @Test
    fun aSleepFailureReachesTheCaller() =
        runTest {
            val round = Round(board, right, seconds = 1)
            assertFailsWith<SleepFailure> {
                Countdown { throw SleepFailure() }.run(round, 0.1, introduce = {}, tick = { round.secondPassed() })
            }
            assertEquals(1, round.secondsLeft)
        }

    @Test
    fun cancellationDuringSleepDoesNotTickEvenIfSleepReturnsNormally() =
        runTest {
            val round = Round(board, right, seconds = 1)
            var ticks = 0
            val job =
                launch {
                    Countdown { currentCoroutineContext().job.cancel() }.run(
                        round,
                        0.1,
                        introduce = {},
                        tick = {
                            ticks += 1
                            round.secondPassed()
                        },
                    )
                }
            job.join()
            assertTrue(job.isCancelled)
            assertEquals(0, ticks)
            assertEquals(1, round.secondsLeft)
        }

    @Test
    fun cancellationDuringIntroductionDoesNotRevealAnotherLetter() =
        runTest {
            val round = Round(board, right, introduces = true)
            val job =
                launch {
                    Countdown { currentCoroutineContext().job.cancel() }.run(
                        round,
                        0.1,
                        introduce = { round.introduceNextLetter() },
                        tick = {},
                    )
                }
            job.join()
            assertTrue(round.introduced.isEmpty())
            assertEquals(Round.Phase.INTRODUCING, round.phase)
        }

    @Test
    fun aRoundFinishedDuringSleepDoesNotReceiveAStaleTick() =
        runTest {
            val round = Round(board, right, seconds = 1)
            var ticks = 0
            Countdown {
                round.touch(Cell(0, 0))
                round.drag(Cell(0, 2))
                round.release()
            }.run(
                round,
                0.1,
                introduce = {},
                tick = {
                    ticks += 1
                    round.secondPassed()
                },
            )
            assertEquals(Round.Phase.WON, round.phase)
            assertEquals(0, ticks)
            assertEquals(1, round.secondsLeft)
        }
}
