package fm.apakabar.lettergrid

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SleepInterval private constructor(
    val seconds: Double,
    val duration: Duration,
) {
    companion object {
        operator fun invoke(seconds: Double): SleepInterval? {
            if (!seconds.isFinite() || seconds <= 0 || seconds >= Long.MAX_VALUE.toDouble()) return null
            val duration = seconds.seconds
            if (duration <= Duration.ZERO) return null
            return SleepInterval(seconds, duration)
        }

        fun enforcing(seconds: Double): SleepInterval =
            checkNotNull(invoke(seconds)) { "Sleep seconds must be a positive, representable duration" }
    }
}
