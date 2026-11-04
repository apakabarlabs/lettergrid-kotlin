package fm.apakabar.lettergrid.ui

import androidx.compose.ui.graphics.Color
import fm.apakabar.lettergrid.SleepInterval
import kotlin.time.Duration

class LetterGridStyle(
    val found: Color,
    val selected: Color,
    val rejected: Color,
    val hinted: Color,
    rejectionSeconds: Double,
) {
    private val rejectionInterval = SleepInterval.enforcing(rejectionSeconds)

    val rejectionSeconds: Double get() = rejectionInterval.seconds

    internal val rejectionDelay: Duration get() = rejectionInterval.duration

    fun copy(
        found: Color = this.found,
        selected: Color = this.selected,
        rejected: Color = this.rejected,
        hinted: Color = this.hinted,
        rejectionSeconds: Double = this.rejectionSeconds,
    ): LetterGridStyle = LetterGridStyle(found, selected, rejected, hinted, rejectionSeconds)

    override fun equals(other: Any?): Boolean =
        other is LetterGridStyle &&
            other.found == found &&
            other.selected == selected &&
            other.rejected == rejected &&
            other.hinted == hinted &&
            other.rejectionSeconds == rejectionSeconds

    override fun hashCode(): Int = listOf(found, selected, rejected, hinted, rejectionSeconds).hashCode()
}
