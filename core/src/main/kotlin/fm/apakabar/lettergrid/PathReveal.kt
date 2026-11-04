package fm.apakabar.lettergrid

class PathReveal(
    cells: List<Cell>,
    elapsed: Double,
    duration: Double,
    reduceMotion: Boolean = false,
) {
    val cells: List<Cell>

    init {
        require(elapsed.isFinite() && duration.isFinite() && duration >= 0) {
            "Path reveal timing must be finite and its duration nonnegative"
        }
        val travel = duration * Generation.settings.reveal.travelShare
        val fraction = if (duration > 0) maxOf(0.0, minOf(1.0, elapsed / travel)) else 1.0
        val shown = if (reduceMotion) cells.size else minOf(cells.size, (fraction * cells.size).toInt() + 1)
        this.cells = cells.take(shown)
    }

    override fun equals(other: Any?): Boolean = other is PathReveal && other.cells == cells

    override fun hashCode(): Int = cells.hashCode()

    companion object {
        val frameSeconds: Double get() = Generation.settings.reveal.frameSeconds
    }
}
