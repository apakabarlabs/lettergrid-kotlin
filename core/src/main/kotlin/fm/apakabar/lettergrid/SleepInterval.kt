package fm.apakabar.lettergrid

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SleepInterval private constructor(
    val seconds: Double,
    val duration: Duration,
) {
    companion object {
        operator fun invoke(seconds: Double): SleepInterval? =
            seconds
                .takeIf { it.isFinite() && it > 0 && it < Long.MAX_VALUE.toDouble() }
                ?.seconds
                ?.takeIf { it > Duration.ZERO }
                ?.let { SleepInterval(seconds, it) }

        fun enforcing(seconds: Double): SleepInterval =
            checkNotNull(invoke(seconds)) { "Sleep seconds must be a positive, representable duration" }
    }
}
