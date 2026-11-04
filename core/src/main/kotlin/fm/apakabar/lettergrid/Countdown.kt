package fm.apakabar.lettergrid

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Countdown(
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) {
    sealed class Failure(
        message: String,
    ) : Exception(message) {
        data object InvalidInterval : Failure("The introduction interval must be a positive, representable duration.")

        data object NoIntroductionProgress : Failure("The introduction callback did not advance the round.")

        data object NoClockProgress : Failure("The tick callback did not advance the round countdown.")
    }

    suspend fun run(
        round: Round,
        secondsPerLetter: Double,
        introduce: () -> Unit,
        tick: () -> Unit,
    ) {
        val interval = SleepInterval(secondsPerLetter) ?: throw Failure.InvalidInterval
        while (round.phase == Round.Phase.INTRODUCING) {
            currentCoroutineContext().ensureActive()
            sleep(interval.duration)
            currentCoroutineContext().ensureActive()
            if (round.phase != Round.Phase.INTRODUCING) break
            val before = round.introduced
            introduce()
            if (round.phase == Round.Phase.INTRODUCING && round.introduced == before) {
                throw Failure.NoIntroductionProgress
            }
        }
        while (round.phase == Round.Phase.PLAYING && round.secondsLeft != null) {
            currentCoroutineContext().ensureActive()
            sleep(1.seconds)
            currentCoroutineContext().ensureActive()
            if (round.phase != Round.Phase.PLAYING || round.secondsLeft == null) break
            val before = round.secondsLeft
            tick()
            if (round.phase == Round.Phase.PLAYING && round.secondsLeft == before) throw Failure.NoClockProgress
        }
    }
}
