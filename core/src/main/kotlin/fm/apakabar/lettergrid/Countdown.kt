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
        repeatWhile(interval, { round.phase == Round.Phase.INTRODUCING }, { round.introduced }, introduce) {
            Failure.NoIntroductionProgress
        }
        val clocked = { round.phase == Round.Phase.PLAYING && round.secondsLeft != null }
        repeatWhile(SleepInterval.enforcing(1.0), clocked, { round.secondsLeft }, tick) { Failure.NoClockProgress }
    }

    private suspend fun repeatWhile(
        interval: SleepInterval,
        running: () -> Boolean,
        state: () -> Any?,
        step: () -> Unit,
        stalled: () -> Failure,
    ) {
        while (running()) {
            currentCoroutineContext().ensureActive()
            sleep(interval.duration)
            currentCoroutineContext().ensureActive()
            if (!running()) break
            val before = state()
            step()
            if (running() && state() == before) throw stalled()
        }
    }
}
