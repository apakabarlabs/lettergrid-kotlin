package fm.apakabar.lettergrid

internal class PlacedRuns(
    val columns: Int,
    val rows: Int,
) {
    private val length: Int get() = Generation.settings.fill.runLength

    fun formed(
        placement: Placement,
        letters: List<String?>,
        among: List<Placement?>,
    ): Boolean =
        placement.cells.any { cell ->
            Direction.lineAxes.any { axis ->
                (0 until length).any { offset ->
                    val run = (0 until length).map { cell.moved(axis, it - offset) }
                    val letter = if (run.all(::isOnBoard)) letters[slot(run[0])] else null
                    if (letter == null || !run.all { letters[slot(it)] == letter }) {
                        false
                    } else {
                        among.none { placed ->
                            placed != null && (placed.containsReading(run) || placed.containsReading(run.asReversed()))
                        }
                    }
                }
            }
        }

    private fun isOnBoard(cell: Cell): Boolean = cell.row in 0 until rows && cell.column in 0 until columns

    private fun slot(cell: Cell): Int = cell.row * columns + cell.column
}
